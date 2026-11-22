/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.2.2";
export const VERSION_CODE = 82200; // major*10000 + minor*1000 + patch*100，随版本单调递增
export const GITHUB_REPO = "xiaobaiba999/Skip-Ad";
export const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPO}`;
export const GITHUB_HOME = `https://github.com/${GITHUB_REPO}`;
export const RELEASES_PAGE = `https://github.com/${GITHUB_REPO}/releases`;
export const ISSUES_PAGE = `https://github.com/${GITHUB_REPO}/issues`;

export interface VersionEntry {
  version: string;
  code: number;
  date: string;
  tagline: string;
  notes: string[];
}

/** 「关于」页仅展示当前安装版本（硬约束：数组只保留最新 1 条，
 *  旧版本内容归档在 GitHub Releases 与 git 历史） */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "8.2.2",
    code: 82200,
    date: "2026-11-22",
    tagline: "日志上传模块简化重构：回归 v7.8 简单模型（采集常开根因修复）+ 8.x 全线召回替换版",
    notes: [
      "根因定案（真实代码实证）：v8.2.1「一键上传提示暂无可上传的新日志」根因是事件采集门被绑在「自动上传开关」上（默认关→appendEvent 直接丢弃一切事件→手动上传必然空转）。v8.2.2 采集常开：日志事件无条件异步落盘、重启保留、自动上传关闭绝不影响采集与手动上传",
      "架构精简：删除 LogBatchStore 六状态机（642 行）/LogUploadDispatcher 守护线程+裁决表+退避表+排空循环（480 行）/LogUploadPolicy 裁决表（141 行），替换为 LogStore（单文件缓冲+幂等清单）+ LogUploader（唯一上传路径）两个小类——失败重试=下次心跳或再点一次，退避表/耗尽终态/复活机制全部删除",
      "六项业务：采集常开·重启保留；自动上传 24h 最多成功 3 次（只记服务端确认·无新日志零请求）；手动上传随时可用（不查本地额度）；成功=HTTP 2xx 且 body ok=true；失败保留+同 batchId 重试（服务端幂等绝不二建 Issue）；终态拒收隔离检视",
      "Issue 链接真实性：成功后经 GitHub 匿名 API 核实公开仓 issues/{n}——核实到才展示可点击的真实 html_url（非拼接）；404 只显示编号不造链接。手动上传空队列四态如实区分（未产生/已传完/采集异常/读取异常）——不再把采集问题甩锅为「暂无日志」",
      "8.x 全线召回（用户指令）：GitHub Releases 删除 v8.0.0/v8.0.3/v8.1.0/v8.2.0 及资产，dist/download 清除全部 8.0.x~8.2.1 APK；v8.2.2 为召回替换版（82200>82100 可覆盖升级·签名与历史一致·零凭据）。测试 39 套件 7068 断言全绿（真实 runUpload 路径+HTTP 替身）；真机 E2E NOT_RUN（沙箱无设备——待装机验证）",
    ],
  },];

/** 比较两个 semver（支持 v 前缀）：a<b → -1，a>b → 1，相等 → 0 */
export function compareVersion(a: string, b: string): number {
  const pa = a.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d > 0 ? 1 : -1;
  }
  return 0;
}
