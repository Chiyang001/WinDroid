# 第三方组件

商店的应用目录根据 WinGet 社区仓库的清单生成，随包附带微软的 MIT 许可。[WinGet 目录快照](https://github.com/microsoft/winget-pkgs/tree/a9b621b900558554630b51fdf78ac83204e4c419)

商店图标来自 WinGet 发布的清单、软件发行方、AppStream 应用目录及社区索引。图标和商标归各自权利人所有，在商店内用于识别对应软件。[图标来源清单](qa/store-icon-provenance.json) · [WinGet 图标字段](https://github.com/microsoft/winget-pkgs/blob/master/doc/manifest/schema/1.28.0/defaultLocale.md) · [Flathub AppStream](https://docs.flathub.org/docs/for-app-authors/metainfo-guidelines) · [Debian 应用目录](https://deb.debian.org/debian/dists/sid/main/dep11/) · [UniGetUI 图标索引](https://github.com/Devolutions/UniGetUI/blob/6b766e194a1197d4a3db2649c2c7b73d5b57aa50/WebBasedData/screenshot-database-v2.json) · [package-icons 第三方声明](https://github.com/memstechtips/package-icons/blob/77ae3c85a95709be9515e9f21b89e15884473dce/THIRD-PARTY-NOTICES)

精选卡片的 7-Zip 标识取自 7-Zip 26.06 x64 官方 MSI 所安装的文件管理器，VLC、Audacity 和 Notepad++ 使用上游项目的图标。[7-Zip](https://github.com/ip7z/7zip/releases/tag/26.06) · [VLC](https://github.com/videolan/vlc/tree/master/share/icons) · [Audacity](https://github.com/audacity/audacity/blob/master/au3/win/audacity.ico) · [Notepad++](https://github.com/notepad-plus-plus/notepad-plus-plus/blob/master/PowerEditor/src/icons/npp.ico)

WinDroid 的运行引擎和部分 Android 代码来自 Bruno D. 和 Winlator 贡献者。Winlator Android 源码固定在 `ecd5ab2980de79eb955621cef26a3b4672013875`，其 LGPL 2.1 文本保留在 [LICENSE](LICENSE)。本工作区保留 WinDroid 使用的上游 Android 源码和原生代码，WinDroid 自有入口位于 `app/src/main/java/app/windroid/`。[上游源码快照](https://github.com/brunodev85/winlator-app/tree/ecd5ab2980de79eb955621cef26a3b4672013875)

## 运行组件及来源

- Wine 10.10（Winlator 定制构建），LGPL 2.1 或更新版。[Wine 源码](https://gitlab.winehq.org/wine/wine)
- Wine Mono 10.1.0 和 Wine Gecko 2.47.4 的原始 MSI 随离线测试 APK 提供，应用使用 Wine 10.10 的 SHA-256 校验值。[Wine Mono 源码与发行版](https://github.com/wine-mono/wine-mono/releases/tag/wine-mono-10.1.0) · [Wine Gecko](https://gitlab.winehq.org/wine/wine/-/wikis/Gecko) · [Wine 的组件版本与校验值](https://github.com/wine-mirror/wine/blob/wine-10.10/dlls/appwiz.cpl/addons.c)
- 离线测试 APK 包含微软提供的 Visual C++ 2013、Visual C++ v14、.NET Desktop Runtime 10.0.15、.NET Framework 4.8、DirectX June 2010 和 WebView2 Runtime 固定版本 157.0.4341.60 x64。Visual C++ v14 使用微软原始 Minimum 和 Additional MSI 与配套 CAB。.NET Framework 4.8 使用微软原始 MSI、CAB、MSP 以及从 .NET Framework 4.0 离线安装包中提取的签名启动加载器和消息资源。各组件按各自条款使用。公开分发含 Visual C++ Redistributable 的版本须按微软的 Visual Studio 许可核对再分发资格。[Visual C++ 再分发条件](https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files) · [.NET Windows 许可](https://github.com/dotnet/core/blob/main/license-information-windows.md) · [.NET Framework 离线部署](https://learn.microsoft.com/dotnet/framework/deployment/deployment-guide-for-developers) · [DirectX 安装包](https://www.microsoft.com/en-us/download/details.aspx?id=8109) · [WebView2 Runtime 分发](https://learn.microsoft.com/microsoft-edge/webview2/concepts/distribution)
- Winlator File Manager 1.5 根据 MIT 许可提供。简体中文版使用固定版本源码和本项目的翻译补丁构建，许可文本随 APK 打包。[上游源码](https://github.com/brunodev85/wfm/tree/6b1860895885a6d65c37815308260293341acb5f) · [翻译补丁和构建脚本](app/src/main/win32/)
- Box64 0.4.4（Winlator 包内构建），MIT。[Box64 源码](https://github.com/ptitSeb/box64)
- Mesa 及 Turnip、Zink、VirGL 图形组件。[Mesa 源码和许可证](https://gitlab.freedesktop.org/mesa/mesa)
- DXVK、D7VK、D8VK、VKD3D 和 cnc-ddraw。[DXVK](https://github.com/doitsujin/dxvk) · [D7VK](https://github.com/WinterSnowfall/d7vk) · [D8VK](https://github.com/AlpyneDreams/d8vk) · [VKD3D](https://gitlab.winehq.org/wine/vkd3d) · [cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw)
- 上游 Linux 用户空间及 glibc 移植。[Termux Pacman glibc 源码和补丁](https://github.com/termux-pacman/glibc-packages)
- Noto Sans CJK SC Regular 作为 Windows 中文回退字体。字体和 SIL Open Font License 1.1 文本随 APK 打包。[Noto CJK 上游](https://github.com/notofonts/noto-cjk)
- 随 APK 打包的 Android 库包括 AndroidX、Material Components、Commons Compress、XZ for Java 和 zstd-jni，版本见 Gradle 依赖声明。[AndroidX](https://github.com/androidx/androidx) · [Material Components](https://github.com/material-components/material-components-android) · [Commons Compress](https://github.com/apache/commons-compress) · [XZ for Java](https://tukaani.org/xz/java.html) · [zstd-jni](https://github.com/luben/zstd-jni)

运行环境归档取自固定的 Winlator 上游快照。上游未提供归档中每个预编译文件的单独构建记录，因此以上链接用于追溯项目来源，定制二进制的对应源码和公开分发授权尚未逐项核对。公开源码仓库通过 [运行环境准备脚本](qa/prepare-runtime-assets.ps1) 从上游获取这些归档及预编译共享库。独立离线运行库安装包由本地构建环境提供。WPS、QQ、微信和 Firefox 的厂商安装包仅用于单独测试。
