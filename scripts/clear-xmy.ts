/* 测试辅助：清除喜马拉雅学习指纹（演示首拦链路用） */
import { db } from "../src/lib/db";
const del = await db.rule.deleteMany({ where: { source: "learned", appName: "喜马拉雅" } });
console.log("deleted:", del.count);
process.exit(0);
