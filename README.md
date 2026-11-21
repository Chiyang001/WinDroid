<p align="center"><img src="./logo.png" alt="WinDroid 软件标志" height="64" align="middle"> &emsp;│&emsp; <a href="https://github.com/Chiyang001"><img src="./developer.png" alt="炽小阳开发者标志" height="64" align="middle"></a></p>

<h1 align="center">WinDroid</h1>

<p align="center"><strong>在 ARM64 安卓手机上运行 Windows 应用</strong></p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0 及以上">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-147BBC" alt="ARM64">
  <img src="https://img.shields.io/badge/Windows-x86%20%7C%20x64-0078D4" alt="支持 Windows x86 和 x64">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-LGPL--2.1-5E6472" alt="LGPL 2.1"></a>
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="#从源码构建">从源码构建</a> ·
  <a href="./COMPATIBILITY.md">真机兼容性</a> ·
  <a href="./THIRD_PARTY_NOTICES.md">第三方组件</a>
</p>

WinDroid 使用 **Wine** 和 **Box64** 在 ARM64 安卓设备上运行 32 位 x86 和 64 位 x64 Windows 应用。它提供独立的 Windows 桌面、EXE / MSI 安装入口，以及适合手机操作的触控和软键盘。默认使用 Box64 稳定预设和 VirGL 图形后端。

## 功能

| 功能 | 使用方式 |
| --- | --- |
| Windows 桌面 | 横屏打开开始菜单、任务栏和文件管理器，从桌面图标启动应用。 |
| 本机安装 | 选择 EXE 或 MSI 安装包，也可从虚拟 `C:` 盘添加便携程序。 |
| 应用商店 | 离线浏览 WinGet 社区目录，联网下载选定版本的安装包并核对 SHA-256。 |
| 触控与键盘 | 切换直接触控或模拟触控板，使用左右键、滚轮及常用键盘快捷键。 |
| 应用快捷方式 | 从 Windows 开始菜单和桌面识别应用，固定到 WinDroid 首页或 Android 桌面。 |
| 本地化 | 默认简体中文，可切换语言和主题，也可自定义 Windows 桌面壁纸。 |

## 快速开始

设备基线为 **ARM64、Android 8.0 或更高版本、4 KB 内存页**，使用 Android 主用户。具体机型和 Windows 软件的实测结果见 [兼容性记录](./COMPATIBILITY.md)。

1. 安装已签名的 APK，打开 WinDroid 并等待「运行环境已就绪」。内置运行环境可离线初始化。
2. 在「主页」点「启动桌面」，或点「安装软件」后选择本机的 EXE / MSI。也可以在「商店」浏览并下载软件。
3. 在「软件」打开已识别的程序。长按程序卡片可固定到首页或添加到手机桌面。
4. 点程序窗口边缘的把手，展开「键盘」「操作」和「退出」。在「操作」切换指针模式，在「键盘」发送 Ctrl+C、Ctrl+V、Ctrl+Alt+Del 等快捷键。

<details>
<summary>窗口操控、程序管理与重置</summary>

- 拖动窗口边缘的把手可换边或调整高度。模拟触控板的左键、右键和滚轮可分别显示，长按控件后可调整位置。
- 直接触控模式下，单指长按触发右键。模拟触控板模式下，长按后原地松手触发右键，长按后拖动可框选。
- 在「软件」点「添加」，可从虚拟盘选取 EXE。菜单优先调用 Windows 卸载程序，也可移除快捷入口或按确认框删除便携程序。
- 在「我的」切换语言或壁纸。Windows 的系统语言、区域设置和壁纸在下次启动时应用。
- 「重置虚拟机」会清除虚拟磁盘、已装软件和快捷记录。重置前请退出 Windows。

</details>

## 商店与运行库

**商店**内置 11,541 条 WinGet 社区目录记录，6,279 条带离线图标。可按分类或关键词搜索，在详情页选择 x86 / x64 安装包。默认优先尝试国内镜像，失败时切换线路并校验 SHA-256。可在详情页改用原始下载源。图标来源见 [来源清单](./qa/store-icon-provenance.json)。

**运行库**位于「我的 → 运行库安装」。完整离线构建可启用或停用 Wine Mono、Wine Gecko、Visual C++ 2013 / v14、.NET Desktop Runtime 和 WebView2 固定版本，也提供 .NET Framework 4.8 和 DirectX June 2010 安装包。具体版本及组件用途见 [第三方组件说明](./THIRD_PARTY_NOTICES.md)。公开源码构建中的可用组件取决于本地提供的离线安装包。

<details>
<summary>运行库和商店数据维护</summary>

- 「内置组件」可为 Direct3D 辅助库、DirectSound、DirectMusic、DirectShow、DirectPlay、XAudio、Visual C++ 2005 / 2010 和 Windows Media Decoder 选择原生或 Wine 实现，下次打开 Windows 时应用。
- 安装 .NET Framework 4.8 前需停用 Wine Mono。DirectX 离线包解压后运行 `DXSETUP.exe`。WebView2 固定版本部署到 `C:\WinDroid\Runtimes\WebView2`，供支持自定义运行库路径的应用使用。
- 目录采用 [WinGet 社区仓库快照](https://github.com/microsoft/winget-pkgs/tree/a9b621b900558554630b51fdf78ac83204e4c419)。使用 Python 和 PyYAML 从 tar 快照生成目录。

```powershell
python qa/build-store-catalog.py winget-pkgs.tar app/src/main/assets/windroid/store-catalog.wdcatalog
```

- [图标处理脚本](./qa) 使用 Python、Pillow、PyYAML、resvg-py 和 `httpx[http2]`，支持 WinGet、发行方资源和 AppStream。合并时校验 WebP 文件及 SHA-256，处理结果写入 [来源清单](./qa/store-icon-provenance.json)。

</details>

## 从源码构建

准备 **JDK 17**、**Android SDK Platform 35**、**Build Tools 34.0.0**、**NDK 24.0.8215888**、**CMake 3.22.1**。设置 `JAVA_HOME` 和 `ANDROID_HOME` 后，在 Windows PowerShell 中运行。

```powershell
.\qa\prepare-runtime-assets.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

准备脚本从固定的 [Winlator 上游快照](https://github.com/brunodev85/winlator-app/tree/ecd5ab2980de79eb955621cef26a3b4672013875) 获取基础运行环境与预编译共享库。脚本也支持通过 `-UpstreamArchive` 使用该快照的本地 ZIP。完整离线构建所用的可选 `.wdpkg` 由本地环境提供，放在 `app/src/main/assets/windroid/offline-runtimes/`，文件名及 SHA-256 见 [运行库清单](./app/src/main/java/app/windroid/WineRuntimePackage.java)。

正式版使用 Release 构建。包含完整离线安装包时，可增加 Gradle 堆内存并限制并行任务数。

```powershell
.\gradlew.bat --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8' :app:testReleaseUnitTest :app:assembleRelease
```

Gradle 会生成 `app/build/outputs/apk/release/app-release-unsigned.apk`。使用自己的 Android 发布密钥和 `apksigner` 签名，再通过 `apksigner verify` 验证。更新已安装的应用时需沿用同一签名密钥。

项目沿用 Winlator 的 `targetSdk 28`，以支持 Box64 执行解包到应用目录中的文件。[Android 行为变更说明](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)

<details>
<summary>本机验收样例</summary>

使用 [QA 构建脚本](./qa/build-fixtures.ps1) 和 LLVM-MinGW 生成 `dist/qa/setup-x86.exe`。在 WinDroid 中从本机安装该 EXE，再从「软件」打开 **WinDroid QA x64**。输入文本，点 **Save test text** 后退出。从首页「最近打开」和 Windows 桌面各打开一次，确认均能读回文本。

</details>

## 开发者与致谢

**炽小阳 · Chiyang001** · [GitHub](https://github.com/Chiyang001) · [哔哩哔哩](https://space.bilibili.com/404891612)

WinDroid 基于 Bruno D. 和贡献者开发的 Winlator，并使用 Wine、Box64 等开源组件。[项目许可证](./LICENSE) · [第三方组件与来源](./THIRD_PARTY_NOTICES.md)
