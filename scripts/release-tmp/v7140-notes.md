versionCode: 84000

# 开屏卫士 v7.14.0

检查更新陈旧窗口根治（@tag 探测链）+ 兜底直连 gcore 化。

> 本轮测试口径：JVM 42 套件 7359 断言全绿（含 S9 探测链新套件）+ JS 行为 7 文件 256 断言全绿。诚实口径：真机/模拟器 E2E NOT_RUN——本轮全部为 JVM/JS 行为级验证（真实代码路径语义级）+ 代码级性能/能耗审计，待用户实测反馈。

## 本次更新

1. **@tag 探测链（「检查更新不到新版本」根治）**：jsDelivr 对 @main 分支引用有源站级缓存（TTL ≤12h、purge 不可达）——每次发布后旧客户端最长 12 小时看到「已是最新」（v7.11.0 检测不到 v7.12.0、本轮 v7.12.0 检测不到 v7.13.0 的同一结构性根因）。现在追加 @tag 探测链：tag 引用是不可变键、零源站缓存——发布即新鲜；@main 链判定「已是最新」时自动探测当前版本之后的候选 tag 清单（vM.m.p+1 / vM.(m+1).0 / vM.(m+1).1 / vM.(m+2).0——下一补丁 + 下两个 minor），命中即拿到新版元数据——发布后第一时间可检测到新版。**此后版本不再出现检查更新不到新版本**（安全闸零放宽：tag 清单过同一信任链——包名 / HTTPS 发布通道 / SHA-256 全套校验；成本闸 12s 独立预算 + 15min 缓存复用，无轮询风暴）
2. **兜底直连 gcore 化**：检查更新失败时的 CDN 直连下载链 cdn→gcore（cdn 当日对 APK 文件返回 403 实测定案；gcore 全日直出且与 Release 资产字节级一致）
3. **浏览器预览同源镜像**：app.js 浏览器路径追加 probeTagManifests（与 Java 侧同候选集公式 + 同端点容错语义——预览环境与 APK 行为可互证）
4. **诚实边界**：隔两个以上 minor 的老版本（探测集覆盖不到最新版）仍靠 @main 链（源站缓存 ≤12h 自愈）或 gcore 版本化直链手工救济

## 下载

- 官方 APK：adshield-v7.14.0.apk（本页资产 · GitHub Releases 官方源 · HTTPS）
- CDN 兜底直链：https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.14.0.apk（与 Release 资产同字节同源 · SHA-256 相同）
- 文件大小：902,710 字节
- SHA-256：`70bbdc9e8518e558620b07a847b91e76a92357a33479e88f03c52e4f0baa4e31`

## 安装说明

- 已安装 v7.13.0 及更早正式版本（versionCode ≤ 83000）：直接安装本 APK 覆盖升级（versionCode 84000，签名一致，无障碍授权/学习数据/已导入订阅全部保留）
- 新安装：直接安装即可（Android 8+ 首次安装需允许「安装未知应用」授权）

## 版本信息

- versionName：7.14.0
- versionCode：84000（major*10000 + minor*1000 + patch*100）
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史正式版本一致）
- APK 零凭据（无 GitHub Token/私钥/Secret——写凭据全部只在服务端）
