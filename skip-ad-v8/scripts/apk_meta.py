#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""APK 元数据极简解析器（更新清单工作流专用 · 零 Android SDK 依赖）。

从 APK 的 AndroidManifest.xml（二进制 AXML）提取三个字段：
  package / versionName / versionCode

为什么不用 aapt：GitHub Actions 生成更新清单需要在 CI 里校验 APK
包名与版本号；自带 AXML 解析（stdlib only，~150 行）让工作流不依赖
Android SDK 安装，且本地/CI 同一份逻辑。基准真值：
  aapt dump badging adshield-v7.9.7.apk
  → package: name='com.zai.adshield' versionCode='79700' versionName='7.9.7'

AXML 格式（AOSP frameworks/base/libs/androidfw/include/androidfw/ResourceTypes.h）：
  文件头 RES_XML_TYPE(0x0003) 8 字节 → 子块序列（type u16 / headerSize u16 / size u32）
   · 字符串池 RES_STRING_POOL_TYPE(0x0001)：UTF-16LE 或 UTF-8(标志位 1<<8)
   · 起始元素 RES_XML_START_ELEMENT_TYPE(0x0102)：attrExt 20 字节 +
     属性数组（每项 20 字节：ns/name/rawValue + typedValue 8 字节）

用法：
  python3 scripts/apk_meta.py <apk 路径>          # 人类可读输出
  python3 scripts/apk_meta.py --json <apk 路径>   # {"package":..., "versionName":..., "versionCode":...}
  作为模块：from apk_meta import apk_meta（返回 dict，缺字段不下锅）
"""
import json
import struct
import sys
import zipfile

RES_STRING_POOL_TYPE = 0x0001
RES_XML_START_ELEMENT_TYPE = 0x0102
UTF8_FLAG = 1 << 8
NO_INDEX = 0xFFFFFFFF

# typedValue.dataType（Res_value.h）
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_BOOL = 0x12


def _read_utf8_len(data, pos):
    """UTF-8 字符串池长度变体：单字节（<0x80）或双字节大端。返回 (值, 新位置)。"""
    b = data[pos]
    pos += 1
    if b & 0x80:
        b = ((b & 0x7F) << 8) | data[pos]
        pos += 1
    return b, pos


def parse_string_pool(data, chunk_start):
    """解析字符串池子块 → list[str]（解析失败返回空表——调用方按缺字段处理）。"""
    try:
        (_t, _hs, _size, string_count, _style_count,
         flags, strings_start, _styles_start) = struct.unpack_from("<HHIIIIII", data, chunk_start)
    except struct.error:
        return []
    is_utf8 = (flags & UTF8_FLAG) != 0
    strings = []
    for i in range(string_count):
        try:
            (off,) = struct.unpack_from("<I", data, chunk_start + 28 + i * 4)
            base = chunk_start + strings_start + off
            if is_utf8:
                _nchars, p = _read_utf8_len(data, base)
                nbytes, p = _read_utf8_len(data, p)
                strings.append(data[p:p + nbytes].decode("utf-8", "replace"))
            else:
                (n,) = struct.unpack_from("<H", data, base)
                p = base + 2
                if n & 0x8000:  # 超长字符串双字长度
                    (n2,) = struct.unpack_from("<H", data, p)
                    n = ((n & 0x7FFF) << 16) | n2
                    p += 2
                strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
        except (struct.error, IndexError):
            strings.append("")
    return strings


def _attr_value(strings, raw_value, tv_type, tv_data):
    """属性值解码：优先 rawValue 字符串索引；否则按 typedValue 类型。"""
    if raw_value != NO_INDEX and raw_value < len(strings):
        return strings[raw_value]
    if tv_type == TYPE_STRING and tv_data < len(strings):
        return strings[tv_data]
    if tv_type == TYPE_INT_DEC or tv_type == TYPE_INT_HEX:
        return tv_data
    if tv_type == TYPE_BOOL:
        return tv_data != 0
    return None


def parse_axml_manifest(data):
    """解析 AXML 二进制 → {'package','versionName','versionCode'}（根元素 manifest 的属性）。"""
    result = {}
    if len(data) < 8:
        return result
    strings = []
    off = 8  # 文件头 8 字节（type/headerSize/size）
    end = len(data)
    while off + 8 <= end:
        chunk_type, _header_size, size = struct.unpack_from("<HHI", data, off)
        if size <= 0 or off + size > end:
            break
        if chunk_type == RES_STRING_POOL_TYPE:
            strings = parse_string_pool(data, off)
        elif chunk_type == RES_XML_START_ELEMENT_TYPE:
            # ResXMLTree_node: 头 8 + lineNumber 4 + comment 4 → attrExt @16：
            #   ns 4 + name 4 + attributeStart 2 + attributeSize 2 + attributeCount 2
            #   + idIndex 2 + classIndex 2 + styleIndex 2（attrExt 共 20 字节）
            try:
                (_ns, name_idx, attr_start, attr_size, attr_count,
                 _id_idx, _cls_idx, _sty_idx) = struct.unpack_from("<IIHHHHHH", data, off + 16)
            except struct.error:
                break
            el_name = strings[name_idx] if name_idx != NO_INDEX and name_idx < len(strings) else ""
            if el_name == "manifest":
                # attributeStart 相对 attrExt 起点（chunk+16）
                attrs_at = off + 16 + attr_start
                for k in range(attr_count):
                    a = attrs_at + k * attr_size
                    if a + 20 > off + size:
                        break
                    (a_ns, a_name, a_raw) = struct.unpack_from("<III", data, a)
                    (_tv_size, _tv_res0, tv_type, tv_data) = struct.unpack_from("<HBBI", data, a + 12)
                    aname = strings[a_name] if a_name != NO_INDEX and a_name < len(strings) else ""
                    if aname == "package":
                        result["package"] = _attr_value(strings, a_raw, tv_type, tv_data)
                    elif aname == "versionName":
                        result["versionName"] = _attr_value(strings, a_raw, tv_type, tv_data)
                    elif aname == "versionCode":
                        result["versionCode"] = tv_data if tv_type in (TYPE_INT_DEC, TYPE_INT_HEX) else None
                if result.get("package"):
                    break  # 根元素已解析——后续无需再扫
        off += size
    return result


def apk_meta(apk_path):
    """APK 路径 → 元数据 dict（读取 AndroidManifest.xml 并解析；异常向上抛）。"""
    with zipfile.ZipFile(apk_path) as z:
        data = z.read("AndroidManifest.xml")
    return parse_axml_manifest(data)


def _main(argv):
    if len(argv) < 2:
        print("用法: apk_meta.py [--json] <apk>", file=sys.stderr)
        return 2
    as_json = argv[1] == "--json"
    path = argv[2] if as_json else argv[1]
    meta = apk_meta(path)
    if as_json:
        print(json.dumps(meta, ensure_ascii=False, sort_keys=True))
    else:
        for k in ("package", "versionName", "versionCode"):
            print("%s: %s" % (k, meta.get(k)))
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
