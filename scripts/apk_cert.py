#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""APK 签名证书指纹提取器（更新清单工作流专用 · 零 Android SDK 依赖）。

从 APK Signing Block（v2 0x7109871A / v3 0xF05368C0 / v3.1 0x1B93AD61）
提取签名者 X.509 证书并计算 SHA-256 指纹，用于发布链「签名证书校验」：
清单生成前核验 APK 与历史正式版本同一签名（防错签/换签发不了覆盖升级）。

为什么不用 apksigner：与 apk_meta.py 同一设计约束——GitHub Actions 生成
更新清单不依赖 Android SDK；std-lib 解析（~130 行）本地/CI 同一份逻辑。
基准真值（2026-11-27 实测，v8.0.4-v8.0.9 全部一致）：
  05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79

APK Signing Block 布局（AOSP ApkSigningBlockUtils，紧邻 ZIP 中央目录之前）：
  [size of block u64][ID-value pairs...][size of block u64]["APK Sig Block 42" 16B]
  pair: [length u64][id u32][value bytes]
  v2 value: signers 序列 → signer: [signed data[digests, certificates], signatures, pubkey]
  v3 value: signers 序列 → signer: [signed data[ digests, certificates ], minSdk, maxSdk, signatures, pubkey]
  （v3 的 certificates 同样位于 signed data 第 2 个长度前缀字段）

用法：
  python3 scripts/apk_cert.py <apk 路径>            # 人类可读输出
  python3 scripts/apk_cert.py --json <apk 路径>      # {"v2":[fp...], "v3":[fp...]}
  作为模块：from apk_cert import apk_cert_sha256（返回 dict，无签名块返回 {}）
"""
import hashlib
import json
import struct
import sys

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0
V31_BLOCK_ID = 0x1B93AD61
SCHEME_NAMES = ((V2_BLOCK_ID, "v2"), (V3_BLOCK_ID, "v3"), (V31_BLOCK_ID, "v3.1"))


class CertError(Exception):
    """签名块结构异常（解析越界/长度矛盾/魔数不符）。"""


def _read_u32(buf, pos):
    if pos + 4 > len(buf):
        raise CertError("u32 out of range at %d" % pos)
    return struct.unpack_from("<I", buf, pos)[0]


def _read_u64(buf, pos):
    if pos + 8 > len(buf):
        raise CertError("u64 out of range at %d" % pos)
    return struct.unpack_from("<Q", buf, pos)[0]


def _read_len_pref(buf, pos):
    """长度前缀字段（u32 长度 + 字节串）→ (字节串, 新位置)。"""
    n = _read_u32(buf, pos)
    pos += 4
    if pos + n > len(buf):
        raise CertError("length-prefixed field overruns buffer")
    return buf[pos:pos + n], pos + n


def _cd_offset(data):
    """EOCD（PK\\x05\\x06）→ ZIP 中央目录偏移（签名块紧邻其前）。"""
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise CertError("EOCD not found")
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]
    return cd_off


def _signing_block_pairs(data):
    """定位 APK Signing Block → {block_id: value_bytes}。无签名块返回 {}。"""
    cd_off = _cd_offset(data)
    magic_off = cd_off - 16
    if magic_off < 0 or data[magic_off:magic_off + 16] != APK_SIG_BLOCK_MAGIC:
        return {}
    size2 = _read_u64(data, magic_off - 8)
    start = cd_off - 8 - size2
    if start < 0:
        raise CertError("signing block start underflow")
    size1 = _read_u64(data, start)
    if size1 != size2:
        raise CertError("signing block size mismatch (%d != %d)" % (size1, size2))
    pairs = {}
    off = start + 8
    end = magic_off - 8
    while off < end:
        plen = _read_u64(data, off)
        off += 8
        if off + plen > end + 8:
            raise CertError("signing block pair overruns block")
        pid = struct.unpack_from("<I", data, off)[0]
        pairs[pid] = data[off + 4: off + plen]
        off += plen
    return pairs


def _signer_certificates(block_value):
    """v2/v3 block value → 各 signer 的证书 DER 列表。

    block value 结构：u32 长度前缀的 signers 序列，每个 signer 又是
    u32 长度前缀的 [signed data[u32 前缀 digests, u32 前缀 certificates],
    signatures, pubkey]——必须先解开 signers 序列层再逐个解 signer
    （少解一层会让 signer 长度读到 signers 总长，整体错位）。证书位于
    signed data 第 2 个 u32 长度前缀字段。
    """
    certs = []
    signers_seq, _ = _read_len_pref(block_value, 0)  # signers 序列层
    pos = 0
    while pos < len(signers_seq):
        signer, pos = _read_len_pref(signers_seq, pos)
        signed_data, _ = _read_len_pref(signer, 0)
        _, p = _read_len_pref(signed_data, 0)  # digests（跳过）
        cert_seq, _ = _read_len_pref(signed_data, p)
        cp = 0
        while cp < len(cert_seq):
            der, cp = _read_len_pref(cert_seq, cp)
            certs.append(der)
    return certs


def apk_cert_sha256(apk_path):
    """APK 路径 → {'v2':[指纹...], 'v3':[指纹...], 'v3.1':[指纹...]}。

    只报告存在的签名方案；无 APK Signing Block（仅 v1 JAR 签名）时返回 {}——
    由调用方按「签名缺失」处理（本项目 minSdk 26，正式构建恒有 v2）。
    """
    with open(apk_path, "rb") as f:
        data = f.read()
    pairs = _signing_block_pairs(data)
    out = {}
    for pid, name in SCHEME_NAMES:
        if pid in pairs:
            certs = _signer_certificates(pairs[pid])
            out[name] = [hashlib.sha256(c).hexdigest() for c in certs]
    return out


def _main(argv):
    if len(argv) < 2:
        print("用法: apk_cert.py [--json] <apk>", file=sys.stderr)
        return 2
    as_json = argv[1] == "--json"
    path = argv[2] if as_json else argv[1]
    certs = apk_cert_sha256(path)
    if as_json:
        print(json.dumps(certs, ensure_ascii=False, sort_keys=True))
    elif not certs:
        print("无 APK Signing Block（仅 v1 JAR 签名或未签名）")
    else:
        for scheme, fps in sorted(certs.items()):
            for fp in fps:
                print("%s: %s" % (scheme, fp))
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
