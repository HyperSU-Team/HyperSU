# HyperSU
<img align='right' src='HyperSU-mini.svg' width='220px' alt="логотип HyperSU">


[English](../README.md) | [简体中文](./zh/README.md) | [日本語](./ja/README.md) | [Türkçe](./tr/README.md) | **Русский**

Решение для получения root-прав на уровне ядра для устройств Android. Форк [`Baka-SU/BakaSU`](https://github.com/Baka-SU/BakaSU), который, в свою очередь, является форком [`SukiSU-Ultra/SukiSU-Ultra`](https://github.com/SukiSU-Ultra/SukiSU-Ultra), а тот — форком [`tiann/KernelSU`](https://github.com/tiann/KernelSU), с добавлением интересных изменений.

[![Latest release](https://img.shields.io/github/v/release/HyperSU-Team/HyperSU?label=Release&logo=github)](https://github.com/HyperSU-Team/HyperSU/releases/latest)
[![Upstream Telegram](https://img.shields.io/badge/Telegram-SukiSU%20(upstream)-blue.svg?logo=telegram)](https://t.me/Sukiksu)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/tiann/KernelSU?logo=gnu)](/LICENSE)

## Особенности

1. Управление доступом `su` и root на уровне ядра.
2. [App Profile](https://kernelsu.org/guide/app-profile.html): закройте root-права для конкретных приложений.
3. Поддержка non-GKI и GKI 1.0.
4. Изменения в теме менеджера и встроенный susfs.

## Статус совместимости

- KernelSU (до v1.0.0) официально поддерживает устройства Android GKI 2.0 (ядро 5.10+).

- Более старые ядра (4.4+) также совместимы, но ядро придется собирать вручную.

- С дополнительными бэкпортами KernelSU может поддерживать ядра серии 3.x (3.4–3.18).

- На данный момент поддерживаются только архитектуры `arm64-v8a`, `armeabi-v7a (bare)` и некоторые `X86_64`.

## Установка

См. [`guide/installation.md`](guide/installation.md)

## Интеграция

См. [`guide/how-to-integrate.md`](guide/how-to-integrate.md)

## Перевод

Если вы хотите предложить перевод для менеджера HyperSU, отправьте Pull Request в [HyperSU-Team/HyperSU](https://github.com/HyperSU-Team/HyperSU). Проект [Crowdin](https://crowdin.com/project/SukiSU-Ultra) принадлежит SukiSU Ultra, верхнему по отношению к BakaSU проекту, и предназначен только для его перевода.

## Устранение неполадок

1. Если устройство зависает при удалении менеджера (HyperSU) 
   Удалите com.sony.playmemories.mobile

## Спонсоры

- [ShirkNeko](https://afdian.com/a/shirkneko)
- [weishu](https://github.com/sponsors/tiann) (автор KernelSU)

## Список спонсоров ShirkNeko

- [Ktouls](https://github.com/Ktouls) Большое спасибо за поддержку.
- [zaoqi123](https://github.com/zaoqi123) Спасибо за чай с молоком.
- [wswzgdg](https://github.com/wswzgdg) Огромное спасибо за поддержку проекта.
- [yspbwx2010](https://github.com/yspbwx2010) Большое спасибо.
- [DARKWWEE](https://github.com/DARKWWEE) 100 USDT
- [Saksham Singla](https://github.com/TypeFlu) Предоставление и поддержка сайта.
- [OukaroMF](https://github.com/OukaroMF) Пожертвование доменного имени для сайта.

## Лицензия

- Файлы в директории «kernel» находятся под лицензией [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
- Изображения файлов `ic_launcher(?!.*alt.*).*` с аниме-персонажами лицензированы особым образом: рисунок [怡子曰曰](https://space.bilibili.com/10545509), авторское право принадлежит [明风 OuO](https://space.bilibili.com/274939213), векторизованные иконки предоставлены этим проектом. Подробнее см. [`LICENSE_icon_English`](./LICENSE_icon_English) и [`LICENSE_icon_SC`](./LICENSE_icon_SC).
- За исключением вышеуказанных файлов и директорий, все остальные части находятся под лицензией [GPL-3.0 or later](https://www.gnu.org/licenses/gpl-3.0.html)

## Благодарности

- [BakaSU](https://github.com/Baka-SU/BakaSU): прямой upstream / основа форка.
- [SukiSU](https://github.com/SukiSU-Ultra/SukiSU-Ultra): upstream BakaSU.
- [KernelSU](https://github.com/tiann/KernelSU): база (основа).
- [MKSU](https://github.com/5ec1cff/KernelSU): Magic Mount.
- [RKSU](https://github.com/rsuntk/KernelsU): поддержка non-GKI.
- [susfs](https://gitlab.com/simonpunk/susfs4ksu): дополнение для скрытия root в ядре и модуль пространства пользователя для KernelSU.
- [KernelPatch](https://github.com/bmax121/KernelPatch): ключевая часть реализации модулей ядра в APatch.

<details>
<summary>Благодарности команды KernelSU</summary>

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): Идея KernelSU.
- [Magisk](https://github.com/topjohnwu/Magisk): Мощный инструмент для получения root-прав.
- [genuine](https://github.com/brevent/genuine/): Проверка подписи APK v2.
- [Diamorphine](https://github.com/m0nad/Diamorphine): Некоторые техники руткитов.
</details>
