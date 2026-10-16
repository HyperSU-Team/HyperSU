# HyperSU
<img align='right' src='SukiSU-mini.svg' width='220px' alt="HyperSU logo">


[English](../README.md) | **简体中文** | [日本語](../ja/README.md) | [Türkçe](../tr/README.md) | [Русский](../ru/README.md)

一个 Android 上基于内核的 root 方案，由 [`SukiSU-Ultra/SukiSU-Ultra`](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 分叉而来，而 SukiSU Ultra 由 [`tiann/KernelSU`](https://github.com/tiann/KernelSU) 分叉而来，添加了一些有趣的变更。

[![最新发行](https://img.shields.io/github/v/release/KirinNova/HyperSU?label=Release&logo=github)](https://github.com/KirinNova/HyperSU/releases/latest)
[![协议: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub 协议](https://img.shields.io/github/license/KirinNova/HyperSU?logo=gnu)](/LICENSE)

## 特性

1. 基于内核的 `su` 和权限管理。
2. [App Profile](https://kernelsu.org/zh_CN/guide/app-profile.html): 把 Root 权限关进笼子里。
3. 支持 non-GKI 与 GKI 1.0。
4. 可调整管理器外观，可自定义 susfs 配置。

## 兼容状态

- KernelSU 官方支持 GKI 2.0 的设备（内核版本 5.10 以上）。

- 旧内核也是兼容的（最低 4.14+），不过需要自己编译内核。

- 通过更多的反向移植，KernelSU 可以支持 3.x 内核（3.4-3.18）。

- 目前支持架构 : `arm64-v8a`、`armeabi-v7a (bare)`、`X86_64`。

## 安装指导

查看 [`guide/installation.md`](guide/installation.md)

## 集成指导

查看 [`guide/how-to-integrate.md`](guide/how-to-integrate.md)

## 参与翻译

要将 HyperSU 翻译成您的语言，或完善现有的翻译，请向 [KirinNova/HyperSU](https://github.com/KirinNova/HyperSU) 提交 Pull Request。

## 故障排除

1. 卸载管理器后系统卡住？
   卸载 _com.sony.playmemories.mobile_

## 感谢上游

HyperSU 站在优秀开源项目的肩膀上。我们衷心感谢上游项目及其维护者。

特别感谢 [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 及其所有贡献者，作为 HyperSU 的直接上游与分叉基础。也深深感谢 [KernelSU](https://github.com/tiann/KernelSU) 及其作者 [tiann](https://github.com/tiann)，创造了最初基于内核的 root 方案，为 SukiSU 与 HyperSU 奠定基础。

同时感谢“鸣谢”部分提到的所有其他上游项目与贡献者。

## 许可证

- 目录 `kernel` 下所有文件为 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 有动漫人物图片表情包的这些文件 `ic_launcher(?!.*alt.*).*` 图标以特别的形式授权，怡子曰曰绘制，版权明风OuO所有。矢量化图标由本项目提供。详见 [`LICENSE_icon_English`](./LICENSE_icon_English) 与 [`LICENSE_icon_SC`](./LICENSE_icon_SC)。
- 除上述文件及目录的其他部分均为 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## 赞助

- [ShirkNeko](https://afdian.com/a/shirkneko) (HyperSU 主要维护者)
- [weishu](https://github.com/sponsors/tiann) (KernelSU 作者)

## ShirkNeko 的赞助列表

- [Ktouls](https://github.com/Ktouls) 非常感谢你给我带来的支持
- [zaoqi123](https://github.com/zaoqi123) 请我喝奶茶也不错
- [wswzgdg](https://github.com/wswzgdg) 非常感谢对此项目的支持
- [yspbwx2010](https://github.com/yspbwx2010) 非常感谢
- [DARKWWEE](https://github.com/DARKWWEE) 感谢老哥的 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) 网站的提供以及维护
- [OukaroMF](https://github.com/OukaroMF) 网站域名捐赠

## 鸣谢

- [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra): 直接上游 / 分叉基础。特别感谢 SukiSU 团队及所有贡献者。
- [KernelSU](https://github.com/tiann/KernelSU): SukiSU 的上游。特别感谢 tiann 及所有 KernelSU 贡献者。
- [FolkPatch](https://github.com/LyraVoid/FolkPatch): 本项目的管理器 UI 移植自该项目。感谢 FolkPatch 团队优秀的 UI/UX 设计工作。特别感谢 LyraVoid 及所有贡献者。
- [MKSU](https://github.com/5ec1cff/KernelSU): 魔法坐骑支持
- [RKSU](https://github.com/rsuntk/KernelsU): non-GKI 支持
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): 隐藏内核补丁以及用户空间模组的 KernelSU 附件
- [KernelPatch](https://github.com/bmax121/KernelPatch): KernelPatch 是内核模块 APatch 实现的关键部分

<details>
<summary>KernelSU 的鸣谢</summary>

- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：KernelSU 的灵感。
- [Magisk](https://github.com/topjohnwu/Magisk)：强大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/)：apk v2 签名验证。
- [Diamorphine](https://github.com/m0nad/Diamorphine)：一些 rootkit 技巧。
</details>
