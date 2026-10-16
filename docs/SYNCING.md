# Syncing with upstream

This repository is a **branch, not a fork**. It shares no commit history with
`SukiSU-Ultra/SukiSU-Ultra`, so a merge or rebase is impossible and every sync is a
manual, per-file port. This document records what that costs and how to avoid the
mistakes it has already caused.

## Why there is no shared history

```
git merge-base main upstream/main   # prints nothing
```

The tree was imported rather than forked, so upstream commits cannot be applied as
commits. `git cherry-pick` and `git merge` will not work. Read a change, then
reproduce it by hand.

## Setup

```
git remote add upstream https://github.com/SukiSU-Ultra/SukiSU-Ultra.git
git -c http.proxy=http://127.0.0.1:31181 \
    -c https.proxy=http://127.0.0.1:31181 \
    -c http.version=HTTP/1.1 -c credential.helper= \
    fetch --filter=blob:none --depth=300 upstream main builtin --no-tags
```

`--filter=blob:none` is not optional. A plain fetch of these refs over a slow link
fails repeatedly with `fatal: early EOF` / `invalid index-pack output`. Tarballs
from `codeload.github.com` are worse: they arrive truncated at random sizes and
`tar` reports `truncated gzip input`.

The remote is configured as a partial clone, so `git checkout upstream/main -- path`
may fail with `unable to read sha1 file` for a blob it has not downloaded. When that
happens, fetch the file directly instead:

```
curl -s --ssl-no-revoke -x http://127.0.0.1:31181 \
  -o path/to/file \
  https://raw.githubusercontent.com/SukiSU-Ultra/SukiSU-Ultra/main/path/to/file
```

## Take only what is needed

Do **not** copy whole directories. Three separate mistakes came from doing that, and
each one shipped a regression:

1. **`kernel/kpm/`, `userspace/ksud/src/kpm.rs`, `CONFIG_KPM`** — KPM was removed
   here on purpose in three commits (`d687bb82`, `2e222acf`, `010ea4ce`). Upstream
   still ships it. Taking upstream's kernel wholesale brought it all back.
2. **`userspace/ksud/Cargo.toml` without `Cargo.lock` and the root `Cargo.toml`** —
   the three are one change. Taking only the manifest left the lock pinning
   `bindgen 0.72.1` while the manifest asked for `0.73`, so cargo upgraded bindgen
   alone and the build died on a `syn` 2 / `syn` 3 mismatch inside bindgen.
3. **`release.yml` globs** — the artifact directories are `Manager` and
   `Manager-spoofed`; the file still listed `manager/*.apk`. Case sensitive, so a
   tagged release would have published no manager APK at all.

Before syncing anything, check whether it was deliberately removed here:

```
git log --oneline --all --grep='drop' -i
git log --oneline --all --diff-filter=D -- <path>
```

## Files that must keep local values

These differ from upstream on purpose. Take upstream's version, then re-apply the
local value.

| File | Local value | Why |
| --- | --- | --- |
| `kernel/Kbuild` | `KSU_PACKAGE_NAME ?= com.hypersu.manager` | The kernel matches the manager by package name. |
| `kernel/Kbuild` | `KSU_EXPECTED_SIZE ?= 0x378`, `KSU_EXPECTED_HASH ?= aaef52d7…` | The release certificate. Upstream's values accept SukiSU's manager and reject ours. |
| `kernel/Kbuild` | `-I$(KSU_KERNEL_DIR)/..` | See "The uapi symlinks" below. |
| `kernel/Kbuild` | `REPO_OWNER := KirinNova`, `REPO_NAME := HyperSU` | Version reporting and the CI commit count. |
| `kernel/Makefile` | `EXTRA_CFLAGS="-I$(MDIR)/.."` | Same reason as the Kbuild include path. |
| `kernel/setup.sh` | clones `KirinNova/HyperSU`, checks out `main` | Upstream checks out the newest tag, which here predates the rebrand and silently downgrades the sources. |
| `userspace/ksud/build.rs` | `KSU_PACKAGE_NAME=com.hypersu.manager` | Must match the manager's `applicationId`. |
| `manager/app/src/main/cpp/CMakeLists.txt` | `include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../..)` | See "The uapi symlinks" below. |
| `.github/workflows/build-manager.yml` | `package_name: com.hypersu.manager[.pr]` | Feeds the kernel's expected package name. |
| `.github/workflows/ksud.yml` | `KSU_PACKAGE_NAME="com.hypersu.manager.pr"` | Same, for the ksud build. |
| `.github/workflows/crowdin.yml` | triggers commented out | This fork has no Crowdin project, so every run failed. |

## The uapi symlinks

Upstream stores the shared headers once at `uapi/` and points two paths at them
with symlinks:

```
kernel/include/uapi              -> ../../uapi
manager/app/src/main/cpp/uapi    -> ../../../../../uapi
```

Windows cannot check a symlink out when `core.symlinks=false` (the default), so git
writes an ordinary file holding the target text. `#include "uapi/..."` then resolves
to something that is not a directory and every such include fails. That is what
stopped the `builtin` sources from building and produced the misleading
`kernel_includes.h:76: fatal error: 'arch.h' file not found`.

Both build systems therefore add the repository root to their include path, which
resolves `uapi/...` whether or not the symlink came through. Do not remove either
line, and do not "fix" the empty `kernel/include/uapi` file.

## Branding

- `namespace` stays `com.sukisu.ultra`. 23 JNI symbols named
  `Java_com_sukisu_ultra_Natives_*` in `manager/app/src/main/cpp/jni.cc` depend on
  it, and the C++ sources use `com.sukisu.ultra` paths.
- `applicationId` is `com.hypersu.manager` (`.pr` for pull requests), set in
  `manager/app/build.gradle.kts`.
- User-visible strings say HyperSU; the `namespace` and the kernel's `[ksu_*]`
  anonymous-inode names do not change. `manager/app/src/main/cpp/ksu.cc` matches the
  kernel's driver by the literal `[ksu_driver]`, so renaming it on one side only
  breaks manager detection.

## Branches

| Branch | Contents |
| --- | --- |
| `main` | Everything: kernel, uapi, userspace, manager, workflows. |
| `builtin` | Kernel sources only, for in-tree builds, plus a `non_gki/` compatibility layer for kernels below 5.10. No `userspace/`, no `.github/`. |

`builtin` is a different design, not an older `main`. Upstream's `builtin` targets
5.10+ only and builds `ksu.o` alone; this branch's `non_gki/` layer needs
declarations upstream has no reason to keep. A wholesale sync of `builtin` was tried
and reverted (`6aaaf1a9`) for exactly that reason — port self-contained fixes, not
directories. `4fd323d0` and `238b7af2` are the pattern to follow.

`kernel/include/uapi` differs per branch: upstream `main` has a symlink, upstream
`builtin` has a real directory, and this branch has an empty placeholder on both.

## After syncing

Nothing here compiles locally — there is no Android SDK and no kernel toolchain on
the development machine. CI is the only real compiler, so a sync is not done until
the workflow is green.

```
git log --oneline -1                       # what you are about to build
```

Then check the run for that commit. The useful endpoints, since `api.github.com` is
intercepted on this machine:

- runs: `https://github.com/KirinNova/HyperSU/actions/workflows/build-manager.yml`
- badge status is CDN cached for a while and can report the previous run

Verify before pushing:

- `git status --short` shows no unexpected deletions
- no BOM in any source file (`grep -rlP '^\xEF\xBB\xBF'`)
- `kernel/Kbuild`, `kernel/Makefile`, `build.rs` and `CMakeLists.txt` still carry
  the local values in the table above
