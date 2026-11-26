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

    // 开屏卫士更新清单：官方 Skip-Ad 仓库为主源（jsDelivr CDN 国内可达，gcore 与 raw 为备用源），
    // kilo 历史仓 raw/jsDelivr 为回退源（8.0.x 旧客户端兼容；发布时两仓同步推送清单），
    // 由 UpdateClient 按序尝试；清单文件由仓库 update-manifest 工作流维护。
    private const val UpdateManifestPath = "xiaobaiba999/Skip-Ad@main/update/latest.json"
    const val StableUpdate = "https://cdn.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateGcore = "https://gcore.jsdelivr.net/gh/$UpdateManifestPath"
    const val StableUpdateRaw = "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json"
    const val StableUpdateLegacyRaw = "https://raw.githubusercontent.com/xiaobaiba999/kilo/main/update/latest.json"
    const val StableUpdateLegacy = "https://cdn.jsdelivr.net/gh/xiaobaiba999/kilo@main/update/latest.json"

    fun githubAssetUpload(path: String) = Github + path

    fun revision(commitId: String, tagName: String?) =
        "$Repository/" + if (tagName != null) "tree/$tagName" else "commit/$commitId"

    fun localServer(host: String, port: Int) = "http://$host:$port"
}
