package li.gkd.app.network

object AppLinks {
    const val Home = "https://github.com/xiaobaiba999"
    const val Repository = "https://github.com/xiaobaiba999/kilo"
    const val Issues = "https://github.com/xiaobaiba999/kilo/issues"
    const val Releases = "https://github.com/xiaobaiba999/kilo/releases"
    const val Github = "https://github.com"
    const val GithubUploadReferer = Repository
    const val GithubGraphql = "$Github/_graphql"
    const val GithubUploadPolicies = "$Github/upload/policies/assets"
    const val PrivilegeRepository = "$Github/priv-kit/priv-kit"
    const val WebView2Download = "https://developer.microsoft.com/microsoft-edge/webview2/"
    const val ErudaScript = "https://registry.npmmirror.com/eruda/latest/files"

    // 开屏卫士更新清单：官方 Skip-Ad 仓库为主源（jsDelivr 四域国内可达，gcore 与 raw 为备用源），
    // kilo 历史仓 raw/jsDelivr 为回退源（8.0.x 旧客户端兼容；发布时两仓同步推送清单），
    // 由 UpdateClient 按序尝试；清单文件由仓库 update-manifest 工作流维护。
    //
    // v8.0.9（2026-11-27 事故补源）：v8.0.8 发布后 cdn+gcore 两域对 @main 清单
    // 持续返回陈旧缓存（17h+ 仍 87100），而 fastly/testingcf 两域当时已新鲜（88000）
    // ——jsDelivr 各接入点边缘缓存独立过期，四域全列后「四域同时陈旧」概率极低。
    private const val UpdateManifestPath = "xiaobaiba999/Skip-Ad@main/update/latest.json"
    private const val UpdateManifestRepo = "xiaobaiba999/Skip-Ad"
    const val StableUpdate = "https://cdn.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateGcore = "https://gcore.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateFastly = "https://fastly.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateTestingcf = "https://testingcf.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateRaw =
        "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json"
    const val StableUpdateLegacyRaw =
        "https://raw.githubusercontent.com/xiaobaiba999/kilo/main/update/latest.json"
    const val StableUpdateLegacy =
        "https://cdn.jsdelivr.net/gh/xiaobaiba999/kilo@main/update/latest.json"

    // @tag 不可变键清单（7.15.x 传承的「发布即新鲜」通道）：tag 指向固定 commit，
    // jsDelivr 对 @tag 无源站分支缓存——发布流程保证清单先于打 tag 提交（tag 树自含
    // 本版清单），探测链即发布后第一时间可用。
    const val UpdateTagProbeHostGcore = "gcore.jsdelivr.net"
    const val UpdateTagProbeHostCdn = "cdn.jsdelivr.net"

    fun stableUpdateTagUrl(host: String, tag: String) =
        "https://$host/gh/$UpdateManifestRepo@$tag/update/latest.json"

    // GitHub Releases API 兜底通道（匿名；versionCode 自 Release 正文的
    // 「versionCode: N」行提取——显式抬码后 tag 公式不可用）。
    const val GithubReleaseApi = "https://api.github.com/repos/$UpdateManifestRepo/releases/latest"

    fun githubAssetUpload(path: String) = Github + path

    fun revision(commitId: String, tagName: String?) =
        "$Repository/" + if (tagName != null) "tree/$tagName" else "commit/$commitId"

    fun localServer(host: String, port: Int) = "http://$host:$port"
}
