# WinDroid 0.1.0 兼容性记录

2026 年 11 月 19 日，在 PKQ110 主用户实测。设备运行 Android 16（API 36），使用 SM6450、`arm64-v8a` 和 4096 字节内存页。

| 项目 | 本次状态 |
| --- | --- |
| Android 项目构建 | APK 构建及 APK Signature Scheme v2 校验通过，原生架构为 `arm64-v8a` |
| 兼容性回归测试 | 19 项 JVM 单元测试通过，覆盖 PE 校验、Windows 快捷方式、注册表应用发现和 XInput 设备枚举。 |
| 离线初始化、x86/x64 样例 | 飞行模式下完成初始化、启动 32 位和 64 位样例。 |
| 触控和软键盘 | 点按和键盘输入可用，Windows 样例成功将输入写入本地文件。 |
| 安装器、快捷方式和保存的数据 | x86 安装器成功安装 x64 样例并生成 `.lnk`。从「我的应用」重新打开后读回了保存的文本。 |
| 整机重启 | 手机重启后从「我的应用」点击已安装的 x64 样例，窗口再次打开并显示之前保存的 `WinDroidQA64`。 |
| 应用图标与 Android 桌面快捷方式 | Firefox 和微信的原始图标以圆角显示。ColorOS 授权后固定 x64 样例至桌面，从桌面点按直达 Windows 窗口。应用进程停止后也能直达，重复点按会回到同一运行会话。 |
| 首页操作与 Windows 桌面 | 「安装 EXE 文件」打开并完成 x86 安装向导。「启动桌面」进入横屏桌面，显示开始菜单、任务栏和已安装程序。双击 x64 样例后读回保存的文字，关闭样例后桌面仍可使用。 |
| 浏览虚拟 C: 盘 | 从 `C:\Program Files\Tencent\Weixin` 手动选择 `Weixin.exe`，并成功保存为「我的应用」入口。 |
| 简体中文 | WinDroid 和 Wine 桌面默认简体中文。运行环境离线准备 `zh_CN.UTF-8`，使用中文的开始菜单与内置文件管理器。第三方程序使用其自带的语言资源。 |
| 初始化中断重试 | 在运行环境更新期间强制停止 WinDroid，重新启动后成功完成更新，原有便携程序入口仍可打开。 |
| 无效文件和不支持的架构 | 损坏的 EXE 与 Windows ARM64 样例均被拒绝，显示对应提示，导入目录没有留下临时文件。 |
| 低存储 | 未在真机模拟。 |
| WPS Office 11.2.0.10443（国际版） | 安装器进程启动，但界面停留在纯红色窗口，安装和本地文档读写未通过。[WPS 官网](https://www.wps.com/) |
| QQ 9.9.37 x64 | 官方安装包实测到达「完成安装」。安装器未创建有效的快捷方式，WinDroid 已能从 Wine 注册表找到 `QQ.exe` 并显示在「软件」页。安装后的第一次启动未显示登录窗口。[腾讯 QQ 下载页](https://im.qq.com/pcqq/) |
| 微信 4.2.0 | 安装向导完成。改用 VirGL 和 Box64 稳定预设后，已实测打开扫码登录窗口并显示二维码。未登录账号验证会话、消息或通话。[微信 Windows 下载页](https://pc.weixin.qq.com/) |
| Firefox 160.0.1 x64 | 安装向导完成并生成快捷方式。VirGL 能继续启动浏览器的内容进程，但几个配置均未得到可操作的浏览器窗口，网页加载未通过。[Firefox 下载页](https://www.firefox.com/zh-CN/download/windows/) |

## 商店专项验证

在 PKQ110 上从底栏进入「商店」，确认显示 11,541 款应用。「办公阅读」筛选出 737 款，搜索 `7-Zip` 后显示 3 个匹配结果。从 7-Zip 26.06 详情选择 x64 MSI，手机下载后计算出的 SHA-256 与 WinGet 清单一致。Windows 安装向导完成后，「软件」页自动识别到 **7-Zip File Manager**。通过该入口打开程序后，确认真实窗口显示，`7zFM.exe` 正常运行。

覆盖安装图标版 APK 后，确认精选页显示 7-Zip 和 Krita 的真实图标。搜索 `Firefox` 时，正式版排在首位，正式版的橙色图标与开发版的蓝色图标均在结果中显示。离线图标覆盖目录中的 1,761 个条目，含 302 个复用同渠道图标的语言版本。

扩充版将离线图标覆盖扩大到 6,279 个条目，占目录的 54.4%。本次新增 4,518 个条目的图标。逐个核对了新增图标的格式、包标识和 SHA-256，并抽查了 Floorp、Atom、Falkon 等应用的图标。

在 PKQ110 上验证商店详情的默认「国内镜像优先」及「仅用原始下载源」切换。以默认线路下载 7-Zip x64 MSI，手机上的文件 SHA-256 与目录一致，Windows 安装向导正常显示。精选大卡片在网络通讯、开发工具和系统工具之间自动轮换，点击打开对应的软件详情。

## 运行库专项验证

在同一台 PKQ110 上使用独立测试前缀和 Wine 10.10。Wine Mono 10.1.0、Wine Gecko 2.47.4 x86 和 x64 均通过 SHA-256 校验。安装命令全部返回成功，前缀中确认生成 Mono 运行库和两个架构的 `xul.dll`。

独立前缀已从同一 Wine 构建补回缺失的 Windows DLL。测试使用 PKQ110 的 Android 16、Wine 10.10 和 Box64 稳定预设。

| 项目 | 真机结果 |
| --- | --- |
| Wine Mono x86 和 x64 | 两个样例均加载 CLR 并写出预期结果。通过 Wine 内部的 `CreateProcessW` 启动并等待时，两个 Windows 子进程的退出码均为 0。Android 直接等待 64 位 Wine 进程时仍可得到 137，Box64 解释模式和去掉显示连接后也复现。安装流程改为读取 Windows 子进程的完整退出码。 |
| Wine Gecko 2.47.4 x86 和 x64 | 两个架构均加载本地 HTML，测得 CSS 布局为 137 × 85，并得到 JavaScript 更新后的 DOM 文本 `JS 42`。网页联网、HTTPS 和 WebView2 另行验证。 |
| Visual C++ 2013 x86 和 x64 | 两个安装器均完成，分别加载 `msvcr120.dll` 并读到原生版本 `12.0.40664.0`，`memset` 导出可用。 |
| Visual C++ v14 x86 和 x64 | 绕过会提前退出的包装程序，直接运行微软的 Minimum 和 Additional MSI。四个 MSI 均完成。两个架构的样例均加载了原生 `vcruntime140.dll` 14.52.37029.0 并成功调用 `memset`。独立测试 APK 的批量停用和重新启用也通过。停用时会还原所选架构中与内置 Microsoft 包完全匹配的残留 DLL。 |
| .NET Desktop Runtime 10.0.15 x86 和 x64 | 两个架构安装成功，`dotnet --list-runtimes` 均列出 `Microsoft.WindowsDesktop.App 10.0.15`。图形应用仍需分别验证。 |
| Microsoft .NET Framework 4.8 | 实际应用内安装在无网络权限的测试 APK 中完成。部署微软 MSI、CAB、MSP 和启动加载器，跳过 NGen 预编译。Release 为 `528049`。x86 和 x64 托管控制台样例均确认加载 Microsoft 原生 `clr.dll`，两个 Windows 子进程退出码均为 0。Windows Forms 和 WPF 尚未验证。 |
| DirectX June 2010 | 离线自解压程序和 `DXSETUP.exe` 均返回 0。x86、x64 测试都加载到版本 `9.29.952.3111` 的 `d3dx9_43.dll`。游戏渲染仍需分别验证。 |
| WebView2 固定版本 x64 | Evergreen 安装器卡在 EdgeUpdate。固定版本 157.0.4341.60 通过离线批量部署和移除测试。SDK 样例显式使用 APK 部署的 `C:\WinDroid\Runtimes\WebView2` 后成功渲染，JavaScript 生成预期标题 `WinDroid WebView2 42`。SDK 的默认检测在测试环境仍返回 `0x80070002`。 |
| 离线批量启用和停用 | 在没有 Android `INTERNET` 权限的测试包中，通过实际 WinDroid Windows 安装 Activity 批量停用 Mono、Gecko x86 和 Gecko x64，逐项确认文件已移除，再从 APK 批量启用并逐项确认文件已恢复。在真机虚拟屏幕上操作「我的 → 运行库安装」，两项 Gecko 均在取消勾选后显示已停用，重新勾选后显示已安装。两套 Gecko MSI 的核心组件 GUID 重复，停用时还需清除所选架构残留的 `xul.dll`。 |
