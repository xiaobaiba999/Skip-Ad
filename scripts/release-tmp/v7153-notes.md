# 开屏卫士 v7.15.3（检查更新三面全崩根治）

> 真机实锤修复轮：用户手机上 v7.15.1 检查更新报「多源均不可达：raw@main 不可达 · jsdelivr@main 不可达 · GitHub API 返回 401」三面全崩——已装用户收不到新版推送。沙箱实测定位：raw.githubusercontent.com 墙内常态不可达 + cdn.jsdelivr.net 国内间歇连接层不可达叠加后，GitHub API 是用户网络里唯一可达的源——却因 APK 内置令牌已失效（401 Bad credentials，实测实锤）被堵死。

## 本次更新

1. **GitHub API 兜底匿名优先（三面全崩的第三面根治）**：公开仓库 /releases/latest 匿名完全可用（60 req/h/IP + 10 分钟缓存绰绰有余）——新通道序：匿名直连 → 内置令牌兜底（限流时才有意义）→ /releases 列表回退；令牌任何状态（有效/失效）不再阻塞检查更新可用性
2. **jsdelivr 四域梯度（第二面根治）**：gcore → cdn → fastly → testingcf 逐域探测，首域成功即进程内 sticky（正常路径零额外请求，抖动域永不再先试）；旧版 cdn.jsdelivr.net 单域写死是清单源的结构性弱点；@tag 探测链域收敛 ≤2（偏好域 + gcore）——4 tag × 3s 预算不膨胀
3. **内置令牌轮换（问题反馈/手动日志上传/8h 自动上传一并恢复）**：旧令牌实测 401 已失效——与检查更新同把令牌，本轮换入新令牌（双层 XOR+Base64 同规约）
4. **测试**：JVM 23 套件 4403 断言全绿（+24：UpdateHostsTest——四域梯度/偏好置顶/tag 域收敛/非法域攻击面收敛；开发中还真抓出一个 sticky 实现缺陷——偏好域不在 @tag 单元素 base 内被丢弃，已修复并有断言钉死）
5. **诚实口径**：沙箱实测四域全 200 + GitHub API 匿名 200 + 旧令牌 401 实锤；真机 E2E 待用户升级后反馈（尤其原「多源均不可达」网络环境）

## 下载

- 官方 APK：adshield-v7.15.3.apk（本页资产）
- CDN 兜底直链：https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.3.apk
- 文件大小：857,654 字节
- SHA-256：65a8d90103fbbea95f51797b842c2737f39c83417bf3f256073e81dfa071b171（注：v7.15.2 Release 正文的 SHA-256 行曾笔误为 2ac189e3…，正确值为该版 apkSha256 字段 a9a658245e9d02a8a6233f3fb8b3ef16bc5cdf9474a5afa0d7ff1ba7615ee1bc——自动更新链校验从未受影响，人工核对请以资产 digest 为准）

## 安装说明

- **已装 v7.15.1/v7.15.2 且检查更新报「多源均不可达」的用户**：手机浏览器打开本 Releases 页手动下载本 APK 覆盖安装（签名一致，授权/学习数据/订阅全保留）——装上后检查更新/自动上传链路即自愈
- 已装 v7.x 任何正式版：直接覆盖升级（85300）

## 版本信息

- versionName：7.15.3 · versionCode：85300
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史一致）
