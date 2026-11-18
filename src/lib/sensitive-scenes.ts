/**
 * 敏感场景零扫描防护（SensitiveScenes · v5.4.0 · 12 类扩容）
 * ─────────────────────────────────────────────────────────────────
 * 【规范源 · 跨端统一】本 TS 实现是跨端规范源：Android 端 Java 类
 * （SensitiveScenes.java，package com.xskip.engine.policy）将逐行对齐
 * 本文件的场景表 / 信号语法 / 检测优先级；任何场景或信号改动必须双端
 * 同步修改，并以本文件的数值与顺序为基准断言（Java 侧一致性用例）。
 *
 * 本库为纯函数库：不 import 数据库 / React，可在服务端与客户端同源使用。
 *
 * 「零扫描」语义：命中即引擎全静默——探针（findAccessibilityNodeInfosByText）
 * 与 DFS 树遍历全部挂起、学习沉淀挂起、日志仅记录场景键不记录内容。
 * 隐私内容（短信正文 / 密码框 / 验证码 / 实名信息）根本不进入内存树遍历，
 * 从机制上杜绝「扫描时读到隐私」的可能，而不只是「读到不外传」。
 *
 * 信号语法（跨端统一，与 Android 端共用同一张表）：
 *   · pkg:pattern   —— 包名匹配（不区分大小写，包含匹配；| 分隔多值）
 *   · cls:pattern   —— 窗口类名匹配（区分大小写，包含匹配；| 分隔多值）
 *   · field:password|otp —— 控件特征（hasPasswordField / hasOtpField 布尔信号）
 *   · text:pattern  —— 控件文案信号（Android 端富上下文消费，Web 检测不消费）
 *   · win:pattern   —— 无障碍窗口类型信号（TYPE_INPUT_METHOD / TYPE_KEYGUARD 等，
 *                      Android 端 AccessibilityWindowInfo.getType() 消费，Web 不消费）
 * 检测消费 pkg / cls / field 三类信号（Web 端输入接口所限）；text / win 信号
 * 供 Android 端在更完整的窗口上下文中做二级确认——两类信号同表维护，
 * 保证「一处改动双端同步」。
 */

/** 敏感场景定义（12 类；zeroScan 恒为 true——命中即全静默） */
export interface SensitiveScene {
  key: string;
  label: string;
  desc: string;
  /** 识别信号（窗口类名 / 包名 / 控件特征，语法见文件头注释） */
  signals: string[];
  /** 零扫描：探针与 DFS 全挂起（本表所有场景恒为 true，类型层面锁死） */
  zeroScan: true;
  /** 引入版本：前 4 项 v3.x 时期沉淀，其余为 v5.4.0 扩容 */
  since: string;
}

/**
 * 敏感场景总表（12 类）。
 * 数组顺序 = 展示顺序（情报站看板按此渲染）；
 * 检测优先级另见 DETECT_ORDER（窄场景优先，防被宽场景先吃掉）。
 */
export const SENSITIVE_SCENES: SensitiveScene[] = [
  {
    key: "payment",
    label: "支付页",
    desc: "收银台 / 付款确认页（PayActivity / CashierActivity 等）。支付动作零容忍误触——任何代点都可能造成资金损失，引擎在该页全静默，交给用户亲手操作。",
    signals: [
      "cls:PayActivity|CashierActivity|CheckoutActivity|PayConfirmActivity",
      "pkg:com.alipay.mobile.payee",
      "text:确认付款|立即支付|支付密码",
    ],
    zeroScan: true,
    since: "v3.2.0",
  },
  {
    key: "password",
    label: "密码输入框",
    desc: "EditText inputType=textPassword / textVisiblePassword，或带密码可见切换控件（text_input_password_toggle / 显示密码开关）的窗口。含密码框即静默——防误触切换可见性、防串号输入。",
    signals: [
      "field:password",
      "cls:PasswordActivity|PswActivity|SetPasswordActivity",
      "text:输入密码|设置密码|密码可见",
    ],
    zeroScan: true,
    since: "v3.2.0",
  },
  {
    key: "otp",
    label: "验证码",
    desc: "验证码 / OTP 输入框（4/6 位分格或单框）+ 验证码文案。短时效凭据，误触即作废；OTP 框常与密码框同窗，双特征任一命中即静默。",
    signals: [
      "field:otp",
      "cls:OtpActivity|VerifyCodeActivity|SmsCodeActivity|CaptchaActivity",
      "text:验证码|校验码|OTP|动态码",
    ],
    zeroScan: true,
    since: "v3.4.0",
  },
  {
    key: "sms",
    label: "短信隐私页",
    desc: "系统短信 / 彩信会话与编写页（com.android.mms 及厂商变体）。通讯内容属最高敏隐私——零扫描意味着树遍历都不发生，正文不进内存。",
    signals: [
      "pkg:com.android.mms|com.android.messaging|com.google.android.apps.messaging|com.samsung.android.messaging",
      "cls:ConversationList|ComposeMessageActivity",
      "text:新信息|编写信息",
    ],
    zeroScan: true,
    since: "v3.4.0",
  },
  {
    key: "settings",
    label: "系统设置",
    desc: "系统设置及全部子页（com.android.settings.*，含开发者选项 / 权限管理入口）。误触开关可能直接改变系统状态（开发者选项 / USB 调试 / 权限授予），一律静默。",
    signals: [
      "pkg:com.android.settings",
      "cls:SettingsActivity|SubSettings",
      "text:开发者选项|系统导航方式",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "ime",
    label: "输入法",
    desc: "TYPE_INPUT_METHOD 窗口（键盘 / 语音输入面板 / 输入法候选条）。输入法窗口上执行任何点击都可能注入按键或抢焦点，属于注入风险区。",
    signals: [
      "win:TYPE_INPUT_METHOD",
      "cls:InputMethodService|ImeService|SoftKeyboard",
      "pkg:.ime.",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "keyguard",
    label: "锁屏",
    desc: "KeyguardManager 锁屏窗（com.android.systemui / 厂商 keyguard 包）。锁屏上的任何点击都可能触发解锁、紧急呼叫或相机误开，全静默。",
    signals: [
      "win:TYPE_KEYGUARD",
      "pkg:com.android.systemui|com.miui.keyguard|keyguard",
      "cls:Keyguard",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "incall",
    label: "通话",
    desc: "InCallUI 通话界面（AOSP com.android.incallui / 三星 com.samsung.android.incallui 及厂商变体）。误触挂断 / 静音 / 免提均不可逆，全静默。",
    signals: [
      "pkg:incallui",
      "cls:InCallActivity|InCallUI|InCallScreen",
      "text:免提|挂断",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "transfer",
    label: "银行转账",
    desc: "银行 / 支付 App 的转账、汇款页（金额输入 + 收款人确认）。与支付页同级零容忍：转账一旦发出不可撤回，窗口内一切点击都禁止。",
    signals: [
      "cls:TransferActivity|TransferConfirmActivity|RemitActivity|RemittanceActivity|BankTransferActivity",
      "text:转账|汇款|确认转账|收款人",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "realname",
    label: "实名认证",
    desc: "实名认证 / 身份信息页（身份证号、真实姓名、人脸识别引导）。法定身份凭据，读一行都属越界，零扫描零日志。",
    signals: [
      "cls:RealNameActivity|RealnameActivity|IdentityVerifyActivity|CertificationActivity|IdCardActivity",
      "text:实名认证|身份证|真实姓名|人脸识别",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "permission",
    label: "系统权限对话框",
    desc: "packageinstaller / permissioncontroller 系统授权弹窗。代点即替用户做权限决定（允许 / 仅本次 / 拒绝），无论方向都不允许，全静默。",
    signals: [
      "pkg:packageinstaller|permissioncontroller",
      "cls:GrantPermissionsActivity|PermissionActivity",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
  {
    key: "a11y_dialog",
    label: "无障碍系统确认对话框",
    desc: "系统开启无障碍服务的 10 秒警告对话框（com.android.settings.accessibility，AccessibilityEnableWarning 系）。绝不能自动点「确认」——误触会导致权限被吊销或安全提示被绕过；也绝不点「取消」，交给用户读完亲手选择。此场景是 v5.2.0「10 秒警告」治理的延续。",
    signals: [
      "cls:AccessibilityEnableWarning|AccessibilityWarningDialog|AccessibilityWarning",
      "pkg:com.android.settings.accessibility",
      "text:超级管理员|强制的辅助服务",
    ],
    zeroScan: true,
    since: "v5.4.0",
  },
];

/**
 * 检测优先级（窄场景 → 宽场景）。
 * a11y_dialog / permission 的包名前缀（com.android.settings.*）会被宽场景
 * settings 先吃掉，故窄场景置前；同为输入类时 otp（含分格 OTP 框）比
 * password（泛密码框）更具体，otp 前置。Java 端按同一顺序遍历。
 */
const DETECT_ORDER: string[] = [
  "a11y_dialog",
  "permission",
  "transfer",
  "realname",
  "payment",
  "otp",
  "password",
  "sms",
  "incall",
  "ime",
  "keyguard",
  "settings",
];

/** key → 场景索引（检测用，避免每次线性 find） */
const SCENE_BY_KEY: ReadonlyMap<string, SensitiveScene> = new Map(
  SENSITIVE_SCENES.map((s) => [s.key, s]),
);

/** 单条信号是否命中（语法见文件头；text / win 信号在 Web 端不消费，返回 false） */
function signalMatches(
  signal: string,
  input: { pkg: string; windowClass: string; hasPasswordField?: boolean; hasOtpField?: boolean },
): boolean {
  const sep = signal.indexOf(":");
  if (sep <= 0) return false;
  const kind = signal.slice(0, sep);
  const pattern = signal.slice(sep + 1);
  switch (kind) {
    case "pkg":
      // 包名：不区分大小写，包含匹配（覆盖厂商子包与前缀变体）
      return pattern
        .split("|")
        .some((p) => input.pkg.toLowerCase().includes(p.toLowerCase()));
    case "cls":
      // 窗口类名：区分大小写（Android 类名驼峰），包含匹配
      return pattern.split("|").some((p) => input.windowClass.includes(p));
    case "field":
      // 控件特征：password / otp 两个布尔信号
      return pattern.split("|").some((f) =>
        f === "password"
          ? input.hasPasswordField === true
          : f === "otp"
            ? input.hasOtpField === true
            : false,
      );
    default:
      // text: / win: 为 Android 端富上下文信号，Web 检测不消费
      return false;
  }
}

/**
 * 检测当前窗口是否命中敏感场景（pkg + 窗口类名 + 输入框特征）。
 * 命中即引擎全静默（探针与 DFS 全挂起）；未命中返回 null。
 * 匹配语义：宁可静默、不可误触——零扫描对体验的代价只是该窗不拦广告，
 * 而误触敏感控件的代价是资金 / 隐私 / 系统状态损失。
 */
export function detectSensitiveScene(input: {
  pkg: string;
  windowClass: string;
  hasPasswordField?: boolean;
  hasOtpField?: boolean;
}): SensitiveScene | null {
  for (const key of DETECT_ORDER) {
    const scene = SCENE_BY_KEY.get(key);
    if (scene && scene.signals.some((sig) => signalMatches(sig, input))) {
      return scene;
    }
  }
  return null;
}

/** 便捷判定：是否零扫描场景（命中任一敏感场景即 true，等价于 detectSensitiveScene 非 null） */
export function isZeroScanScene(input: {
  pkg: string;
  windowClass: string;
  hasPasswordField?: boolean;
  hasOtpField?: boolean;
}): boolean {
  return detectSensitiveScene(input) !== null;
}
