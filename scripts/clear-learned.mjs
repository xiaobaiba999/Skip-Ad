import { PrismaClient } from "@prisma/client";
const db = new PrismaClient();
const deleted = await db.rule.deleteMany({ where: { source: "learned" } });
console.log(`已清除学习规则 ${deleted.count} 条`);
await db.$disconnect();
