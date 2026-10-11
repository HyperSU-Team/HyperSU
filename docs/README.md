# HyperSU
<img align='right' src='SukiSU-mini.svg' width='220px' alt="HyperSU logo">


**English** | [简体中文](./zh/README.md) | [日本語](./ja/README.md) | [Türkçe](./tr/README.md) | [Русский](./ru/README.md)

A kernel-based root solution for Android devices, forked from [`SukiSU-Ultra/SukiSU-Ultra`](https://github.com/SukiSU-Ultra/SukiSU-Ultra), which is forked from [`tiann/KernelSU`](https://github.com/tiann/KernelSU), and added some interesting changes.

[![Latest release](https://img.shields.io/github/v/release/KirinNova/HyperSU?label=Release&logo=github)](https://github.com/KirinNova/HyperSU/releases/latest)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/KirinNova/HyperSU?logo=gnu)](/LICENSE)

## Features

1. Kernel-based `su` and root access management
2. [App Profile](https://kernelsu.org/guide/app-profile.html): Lock up the root power in a cage
3. Support non-GKI and GKI 1.0
4. KPM Support
5. Tweaks to the manager theme and the built-in susfs management tool.

## Compatibility Status

- KernelSU (before v1.0.0) officially supports Android GKI 2.0 devices (kernel 5.10+).

- Older kernels (4.4+) are also compatible, but the kernel will have to be built manually.

- With more backports, KernelSU can supports 3.x kernel (3.4-3.18).

- Currently, only `arm64-v8a`, `armeabi-v7a (bare)` and `X86_64`(some) are supported.

## Installation

See [`guide/installation.md`](guide/installation.md)

## Integration

See [`guide/how-to-integrate.md`](guide/how-to-integrate.md)

## Translation

If you need to submit a translation for the manager, please open a pull request on GitHub.

## KPM Support

- Based on KernelPatch, we removed features redundant with KSU and retained only KPM support.
- Work in Progress: Expanding APatch compatibility by integrating additional functions to ensure compatibility across different implementations.

**Open-source repository**: [https://github.com/ShirkNeko/SukiSU_KernelPatch_patch](https://github.com/ShirkNeko/SukiSU_KernelPatch_patch)

**KPM template**: [https://github.com/udochina/KPM-Build-Anywhere](https://github.com/udochina/KPM-Build-Anywhere)

> [!Note]
>
> 1. Requires `CONFIG_KPM=y`
> 2. Non-GKI devices requires `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`
> 3. For kernels below `4.19`, backporting from `set_memory.h` from `4.19` is required.

## Troubleshooting

1. Device stuck upon manager app uninstallation?
   Uninstall _com.sony.playmemories.mobile_

## Sponsor

- [ShirkNeko](https://afdian.com/a/shirkneko) (maintainer of HyperSU)
- [weishu](https://github.com/sponsors/tiann) (author of KernelSU)

## ShirkNeko's sponsorship list

- [Ktouls](https://github.com/Ktouls) Thanks so much for bringing me support.
- [zaoqi123](https://github.com/zaoqi123) Thanks for the milk tea.
- [wswzgdg](https://github.com/wswzgdg) Many thanks for supporting this project.
- [yspbwx2010](https://github.com/yspbwx2010) Many thanks.
- [DARKWWEE](https://github.com/DARKWWEE) 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) Provide and maintain the website
- [OukaroMF](https://github.com/OukaroMF) Donation of website domain name

## Acknowledgments

HyperSU is built on the shoulders of excellent open-source projects. We would like to express our sincere gratitude to the upstream projects and their maintainers.

In particular, we thank [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) and all of its contributors for serving as the direct upstream and fork base of HyperSU. We also deeply thank [KernelSU](https://github.com/tiann/KernelSU) and its author [tiann](https://github.com/tiann) for creating the original kernel-based root solution and laying the foundation for SukiSU and HyperSU.

Thanks also go to all other upstream projects and contributors mentioned in the Credit section for their open-source work and continuous contributions.

## Upstream maintainers and contributors

HyperSU would not exist without the people who build and maintain [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra), the project it is forked from. Our thanks go to all of them.

Maintainer, as named by the upstream project:

- [ShirkNeko](https://github.com/ShirkNeko)

Contributors, by the work they have landed upstream:

- [rsuntk](https://github.com/rsuntk) — non-GKI support, RKSU lineage
- [5ec1cff](https://github.com/5ec1cff) — Magic Mount (MKSU)
- Wang Han
- [u9521](https://github.com/u9521)
- AlexLiuDev233
- [backslashxx](https://github.com/backslashxx)
- [fluffball3](https://github.com/fluffball3)
- [JackAltman](https://github.com/JackA1ltman)
- [xixiaobei](https://github.com/xixiaobei-bei)
- Ylarod
- [cctv18](https://github.com/cctv18)
- [libingxuan](https://github.com/aaaaaaaa-815)
- [XiaoTong6666](https://github.com/XiaoTong6666)
- [Xiaomichael](https://github.com/Xiaomichael)
- [Small-cart](https://github.com/Small-cart)
- [s-b-repo](https://github.com/s-b-repo)
- [Kendox07](https://github.com/Kendox07)
- [Coconutat](https://github.com/Coconutat)
- [bXZb](https://github.com/bXZb)
- [awkoo](https://github.com/awkoo)
- [FunLay123](https://github.com/FunLay123)
- [hmtheboy154](https://github.com/hmtheboy154)
- [JiuGeFaCai](https://github.com/JiuGeFaCai)
- [mgiganto](https://github.com/mgiganto)
- [mihoy3rd](https://github.com/mihoy3rd)
- [xiaozhou26](https://github.com/xiaozhou26)
- [fatalcoder524](https://github.com/fatalcoder524)
- [YuKongA](https://github.com/YuKongA)

And everyone else who has contributed to SukiSU Ultra, KernelSU, or the projects listed under Credit, including those whose commits are not named here.

## License

- The file in the “kernel” directory is under [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) license.
- The images of the files `ic_launcher(?!.*alt.*).*` with anime character artwork are licensed under a special arrangement: drawn by [怡子曰曰](https://space.bilibili.com/10545509), the copyright is held by [明风 OuO](https://space.bilibili.com/274939213), and the vectorized icons are provided by this project. See [`LICENSE_icon_English`](./LICENSE_icon_English) and [`LICENSE_icon_SC`](./LICENSE_icon_SC) for details.
- Except for the files or directories mentioned above, all other parts are under [GPL-3.0 or later](https://www.gnu.org/licenses/gpl-3.0.html) license.

## Credit

- [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra): direct upstream / fork base. Special thanks to the SukiSU team and all contributors.
- [KernelSU](https://github.com/tiann/KernelSU): upstream of SukiSU. Special thanks to tiann and all KernelSU contributors.
- [FolkPatch](https://github.com/LyraVoid/FolkPatch): The manager UI is ported from this project. We thank the FolkPatch team for their excellent UI/UX design work. Special thanks to LyraVoid and all contributors.
- [MKSU](https://github.com/5ec1cff/KernelSU): Magic Mount
- [RKSU](https://github.com/rsuntk/KernelsU): support non-GKI
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): An addon root hiding kernel patches and userspace module for KernelSU.
- [KernelPatch](https://github.com/bmax121/KernelPatch): KernelPatch is a key part of the APatch implementation of the kernel module

<details>
<summary>KernelSU's credit</summary>

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk): The powerful root tool.
- [genuine](https://github.com/brevent/genuine/): APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine): Some rootkit skills.
</details>
