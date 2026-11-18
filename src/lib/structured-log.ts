/**
 * 结构化日志体系（StructuredLog · v5.4.0 · schema 1.0）
 * ─────────────────────────────────────────────────────────────────
 * 【规范源 · 跨端统一】本 TS 实现是跨端规范源：Android 端 Java 类
 * （StructuredLogger.java，package com.xskip.engine.log）将逐行对齐
 * 本文件的级别序 / 单行格式 / 脱敏规则；任何改动必须双端同步，
 * 保证 Web 端解析器可直接读取 Android 端导出的日志文件。
 *
 * 本库为纯函数库：不 import 数据库 / React，可在服务端与客户端同源使用。
 *
 * 单行格式（跨端逐字节一致，日志导出/导入互相可解析）：
 *   2026-11-20T12:00:00.123Z [INFO][engine] window.attach {"pkg":"com.xmbn.motor"}
 *   {ts ISO8601 带毫秒} [{LEVEL 大写}][{module}] {event 点分命名} {JSON data}
 * data 为空（或缺键）时不附加 JSON 段。
 *
 * 事件命名规范（点分，动词结尾）：
 *   engine:     window.attach / probe.hit / profile.clean.mark / action.click
 *   scheduler:  boot.begin / tier.classify / policy.apply
 *   quality:    rule.match.hit / learn.sample.reject / wilson.update
 *   guard:      sensitive.zero_scan / guard.service.keep
 *   keepalive:  ping.send / revive.notify
 *   sync:       source.pull / rule.merge.dedup
 *   ui:         panel.open / export.run
 */

/** 日志级别（LEVEL_ORDER 为级别序：数值越高越严重） */
export type LogLevel = "trace" | "debug" | "info" | "warn" | "error";

/** 结构化日志 schema 版本（格式 / 脱敏规则变更时递增，解析器按版本兼容） */
export const LOG_SCHEMA_VERSION = "1.0";

/** 级别序（模块 logger 按此过滤：低于最低级别的条目直接丢弃，不产生字符串） */
export const LEVEL_ORDER: Record<LogLevel, number> = {
  trace: 0,
  debug: 1,
  info: 2,
  warn: 3,
  error: 4,
};

/** 模块白名单（跨端统一；Web 端解析器据此校验 module 字段） */
export const LOG_MODULES = [
  "engine",
  "scheduler",
  "quality",
  "guard",
  "keepalive",
  "sync",
  "ui",
] as const;

/** 单条结构化日志（ts 为 ISO8601 带毫秒字符串） */
export interface LogEntry {
  ts: string;
  level: LogLevel;
  module: string;
  event: string;
  data?: Record<string, unknown>;
}

/** 文本类键名：脱敏时截断至 LOG_TEXT_TRUNCATE 字符（防隐私长文本落盘） */
const TEXT_LIKE_KEYS = new Set(["text", "content", "desc", "title", "query"]);
/** 包名类键名：完整保留（规则匹配与档案键，非用户隐私内容） */
const PKG_LIKE_KEYS = new Set(["pkg", "pkgName", "package"]);

/** 文本截断长度（字符） */
export const LOG_TEXT_TRUNCATE = 32;

/**
 * 便捷脱敏：日志 data 中的 pkg 保留、text 类内容截断 32 字符。
 * 规则（跨端一致）：
 *   · pkg / pkgName / package —— 原样保留（拦截决策与档案查询的键，不含隐私正文）
 *   · text / content / desc / title / query —— 超长截断至 32 字符并加省略号
 *   · 嵌套对象递归脱敏；数字 / 布尔 / null / 数组原样保留
 * 返回新对象（纯函数，不修改入参）。
 */
export function sanitizeLogData(data: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(data)) {
    if (PKG_LIKE_KEYS.has(k)) {
      out[k] = v;
    } else if (typeof v === "string" && TEXT_LIKE_KEYS.has(k)) {
      out[k] = v.length > LOG_TEXT_TRUNCATE ? v.slice(0, LOG_TEXT_TRUNCATE) + "…" : v;
    } else if (v !== null && typeof v === "object" && !Array.isArray(v)) {
      out[k] = sanitizeLogData(v as Record<string, unknown>);
    } else {
      out[k] = v;
    }
  }
  return out;
}

/**
 * 单行格式化：`{ts} [{LEVEL}][{module}] {event} {json}`。
 * data 缺省或无键 → 不附加 JSON 段（行尾止于 event）。
 * JSON.stringify 键序按插入序稳定输出（Java 端按 LinkedHashMap 插入序对齐）。
 */
export function formatLog(e: LogEntry): string {
  const payload =
    e.data && Object.keys(e.data).length > 0 ? ` ${JSON.stringify(e.data)}` : "";
  return `${e.ts} [${e.level.toUpperCase()}][${e.module}] ${e.event}${payload}`;
}

/**
 * 模块级 logger 工厂：绑定模块名与最低级别。
 * 低于 minLevel 的调用返回 null（条目被丢弃，不产生字符串、不写盘）；
 * 通过的条目自动生成 ts（ISO8601 带毫秒）并对 data 应用 sanitizeLogData
 * 脱敏（防隐私长文本落盘——makeLogger 出口即脱敏，业务侧无需手动调用）。
 */
export function makeLogger(
  module: string,
  minLevel: LogLevel,
): {
  log: (
    level: LogLevel,
    event: string,
    data?: Record<string, unknown>,
  ) => LogEntry | null;
} {
  return {
    log(
      level: LogLevel,
      event: string,
      data?: Record<string, unknown>,
    ): LogEntry | null {
      if (LEVEL_ORDER[level] < LEVEL_ORDER[minLevel]) return null;
      const entry: LogEntry = {
        ts: new Date().toISOString(),
        level,
        module,
        event,
        ...(data !== undefined ? { data: sanitizeLogData(data) } : {}),
      };
      return entry;
    },
  };
}
