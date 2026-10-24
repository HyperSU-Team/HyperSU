#!/bin/sh
set -eu

# HyperSU kernel integration script.
#
# Supports both GKI and legacy (non-GKI) Android kernels. The kernel version is
# detected from the target tree and selects the matching object set at build time
# (see Kbuild); this script only wires the sources into the kernel tree, so the
# same checkout works for both.

GKI_ROOT=$(pwd)
KERNEL_ROOT=$GKI_ROOT

KSU_REPO=${KSU_REPO:-https://github.com/HyperSU-Team/HyperSU.git}
KSU_BRANCH=${KSU_BRANCH:-main}
KSU_DIR=${KSU_DIR:-$KERNEL_ROOT/KernelSU}
MANAGED_MARKER=$KSU_DIR/.git/hyper-su-setup-managed

display_usage() {
    echo "Usage: $0 [--cleanup | <commit-or-tag>]"
    echo "  --cleanup:              Cleans up previous modifications made by the script."
    echo "  <commit-or-tag>:        Sets up or updates KernelSU to the specified tag or commit."
    echo "  -h, --help:             Displays this usage information."
    echo "  (no args):              Sets up or updates KernelSU to the latest version of the default branch."
    echo
    echo "Environment overrides: KSU_REPO, KSU_BRANCH, KSU_DIR"
}

initialize_variables() {
    if test -d "$KERNEL_ROOT/common/drivers"; then
        DRIVER_DIR=$KERNEL_ROOT/common/drivers
        KERNEL_SOURCE_DIR=$KERNEL_ROOT/common
    elif test -d "$KERNEL_ROOT/drivers"; then
        DRIVER_DIR=$KERNEL_ROOT/drivers
        KERNEL_SOURCE_DIR=$KERNEL_ROOT
    else
        echo '[ERROR] Neither "drivers/" nor "common/drivers/" was found.' >&2
        exit 127
    fi

    DRIVER_MAKEFILE=$DRIVER_DIR/Makefile
    DRIVER_KCONFIG=$DRIVER_DIR/Kconfig
    KCONFIG_ENTRY='source "drivers/kernelsu/Kconfig"'
    MAKEFILE_ENTRY='obj-$(CONFIG_KSU) += kernelsu/'
}

read_kernel_version() {
    KERNEL_MAKEFILE=$KERNEL_SOURCE_DIR/Makefile
    KERNEL_MAJOR=$(sed -n 's/^VERSION = \([0-9][0-9]*\)$/\1/p' "$KERNEL_MAKEFILE" | head -n 1)
    KERNEL_PATCHLEVEL=$(sed -n 's/^PATCHLEVEL = \([0-9][0-9]*\)$/\1/p' "$KERNEL_MAKEFILE" | head -n 1)

    if test -z "$KERNEL_MAJOR" || test -z "$KERNEL_PATCHLEVEL"; then
        echo '[!] Could not determine the kernel version; compatibility will be detected at build time.'
        return
    fi

    echo "[+] Kernel version: $KERNEL_MAJOR.$KERNEL_PATCHLEVEL"
    if test "$KERNEL_MAJOR" -lt 5 || { test "$KERNEL_MAJOR" -eq 5 && test "$KERNEL_PATCHLEVEL" -lt 10; }; then
        echo '[+] Integration mode: legacy (non-GKI, < 5.10) SELinux compatibility layer.'
    else
        echo '[+] Integration mode: native HyperSU SELinux hide (>= 5.10).'
    fi
}

perform_cleanup() {
    echo '[+] Cleaning up...'

    if test -L "$DRIVER_DIR/kernelsu"; then
        rm "$DRIVER_DIR/kernelsu"
        echo '[-] Removed drivers/kernelsu symlink.'
    fi

    if grep -Fqx "$MAKEFILE_ENTRY" "$DRIVER_MAKEFILE"; then
        sed -i '\|^obj-$(CONFIG_KSU) += kernelsu/$|d' "$DRIVER_MAKEFILE"
        echo '[-] Reverted drivers Makefile.'
    fi

    if grep -Fqx "$KCONFIG_ENTRY" "$DRIVER_KCONFIG"; then
        sed -i '\|^source "drivers/kernelsu/Kconfig"$|d' "$DRIVER_KCONFIG"
        echo '[-] Reverted drivers Kconfig.'
    fi

    if test -f "$MANAGED_MARKER"; then
        if test -n "$(git -C "$KSU_DIR" status --porcelain)"; then
            echo "[!] Kept $KSU_DIR because it contains local changes."
        else
            rm -rf "$KSU_DIR"
            echo '[-] Removed the cloned KernelSU directory.'
        fi
    elif test -d "$KSU_DIR/.git"; then
        echo "[!] Kept $KSU_DIR because it was not created by this script."
    fi

    echo '[+] Cleanup complete.'
}

checkout_kernelsu() {
    REVISION=${1-}

    if test -e "$KSU_DIR" && ! test -d "$KSU_DIR/.git"; then
        echo "[ERROR] $KSU_DIR exists but is not a Git repository." >&2
        exit 1
    fi

    if ! test -d "$KSU_DIR/.git"; then
        git clone --branch "$KSU_BRANCH" --single-branch "$KSU_REPO" "$KSU_DIR"
        touch "$MANAGED_MARKER"
        echo '[+] Repository cloned.'
    else
        if test -n "$(git -C "$KSU_DIR" status --porcelain)"; then
            echo "[ERROR] $KSU_DIR contains local changes; commit or remove them before updating." >&2
            exit 1
        fi
        git -C "$KSU_DIR" fetch origin --tags "$KSU_BRANCH"
        echo '[+] Repository updated.'
    fi

    # The Makefile derives the reported version from the main branch. A
    # --single-branch clone of a different branch would not have it, so fetch main
    # into a remote-tracking ref, which is what the Makefile reads.
    #
    # Two separate commands on purpose: a fetch with an explicit refspec does not
    # follow tags, and the tags supply the tag component of the version string.
    git -C "$KSU_DIR" fetch --tags origin 2>/dev/null || true
    git -C "$KSU_DIR" fetch origin "main:refs/remotes/origin/main" 2>/dev/null || true

    if test -n "$REVISION"; then
        git -C "$KSU_DIR" checkout --detach "$REVISION"
        echo "[+] Checked out $REVISION."
    else
        git -C "$KSU_DIR" checkout -B "$KSU_BRANCH" "origin/$KSU_BRANCH"
        echo "[+] Checked out $KSU_BRANCH."
    fi
}

integrate_kernelsu() {
    KSU_KERNEL_DIR=$KSU_DIR/kernel
    if ! test -f "$KSU_KERNEL_DIR/Kconfig"; then
        echo "[ERROR] KernelSU sources were not found at $KSU_KERNEL_DIR." >&2
        exit 1
    fi

    RELATIVE_KSU_DIR=$(realpath --relative-to="$DRIVER_DIR" "$KSU_KERNEL_DIR")
    ln -sfn "$RELATIVE_KSU_DIR" "$DRIVER_DIR/kernelsu"
    echo '[+] Linked drivers/kernelsu.'

    if ! grep -Fqx "$MAKEFILE_ENTRY" "$DRIVER_MAKEFILE"; then
        printf '\n%s\n' "$MAKEFILE_ENTRY" >> "$DRIVER_MAKEFILE"
        echo '[+] Updated drivers Makefile.'
    fi

    if ! grep -Fqx "$KCONFIG_ENTRY" "$DRIVER_KCONFIG"; then
        printf '\n%s\n' "$KCONFIG_ENTRY" >> "$DRIVER_KCONFIG"
        echo '[+] Updated drivers Kconfig.'
    fi
}

setup_kernelsu() {
    echo '[+] Setting up HyperSU...'
    read_kernel_version
    checkout_kernelsu "${1-}"
    integrate_kernelsu
    echo '[+] Done. Enable CONFIG_KSU and build the kernel normally.'
}

case ${1-} in
    '')
        initialize_variables
        setup_kernelsu
        ;;
    -h|--help)
        display_usage
        ;;
    --cleanup)
        initialize_variables
        perform_cleanup
        ;;
    *)
        if test "$#" -ne 1; then
            display_usage >&2
            exit 2
        fi
        initialize_variables
        setup_kernelsu "$1"
        ;;
esac
