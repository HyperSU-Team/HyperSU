// SPDX-License-Identifier: GPL-2.0
//
// Bridge between the SUSFS kernel patch and this tree's runtime hooking.
//
// The SUSFS patch edits core kernel files and calls a fixed set of ksu_*
// symbols from them - SYSCALL_DEFINE3(read), vfs_statx_fd(), do_faccessat(),
// __do_execve_file(), SYSCALL_DEFINE3(setresuid), SYSCALL_DEFINE4(reboot).
// Those call sites are compiled into the kernel, so the symbols must exist at
// link time whether or not this build drives the same behaviour through its own
// syscall hooks.
//
// The two mechanisms overlap: this tree already intercepts read and fstat by
// patching the syscall table, and reaches su via execveat. What the kernel-side
// call sites need is therefore not a second implementation but a thin adapter
// that feeds the existing handlers, plus no-ops where the syscall-table path is
// the one that does the work.
//
// The file is not under compat/ because it is legacy-only - it is built on every
// kernel, since any kernel carrying the patch has these call sites. Each entry
// point is gated on CONFIG_KSU_SUSFS to match the guards around them, and the
// fallback below keeps the translation unit non-empty when the patch is absent.

#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/types.h>
#include <linux/version.h>

#ifdef CONFIG_KSU_SUSFS

/*
 * vfs_statx_fd() reports the size of the file behind an fd. The kernel patch
 * calls this after vfs_getattr() so the result can be adjusted before it reaches
 * userspace, which is how init.rc's size is spoofed on newer trees.
 *
 * This tree does that through its own fstat syscall hook instead, so the value
 * has already been fixed up by the time the injected call runs. There is nothing
 * left to adjust, and writing to *kstat_size_ptr here would double-apply it.
 */
void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)
{
    (void)fd;
    (void)kstat_size_ptr;
}

/*
 * Called from vfs_statx() with the path about to be resolved, so su can be
 * redirected to ksud before the lookup happens. This tree performs the same
 * redirect from its newfstatat hook, which owns the filename pointer and the
 * syscall it re-enters, so taking over here would resolve the path twice.
 */
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    (void)dfd;
    (void)filename;
    (void)flags;
    return 0;
}

/*
 * Same split as ksu_handle_stat(): the faccessat redirect belongs to this tree's
 * faccessat hook, and the kernel-side call site only needs the symbol.
 *
 * The kernel passes NULL for the last argument today, but the declared type is
 * int * and the definition has to match it exactly.
 */
int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *flags)
{
    (void)dfd;
    (void)filename;
    (void)mode;
    (void)flags;
    return 0;
}

/*
 * __do_execve_file() asks this one whether the exec is a su request, and uses
 * the answer to decide which sucompat handler to run:
 *
 *     is_su_session = !ksu_handle_execveat(&fd, &filename, &argv, &envp, &flags);
 *
 * Returning 0 says "yes, su session" and would send the kernel down the
 * sdcard-not-decrypted branch. This tree resolves su from its own execveat hook,
 * where it can rewrite the filename before the syscall runs, so the honest
 * answer here is 1: not handled, carry on.
 */
int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    (void)fd;
    (void)filename_ptr;
    (void)argv;
    (void)envp;
    (void)flags;
    return 1;
}

/*
 * The kernel calls this for the non-decrypted-sdcard case, where su has to be
 * resolved without touching the path the caller passed. This tree has no
 * separate handler for that shape - its execveat hook covers both - so report
 * "not a su session" and let the normal exec proceed.
 *
 * The name collides with the syscall-table entry point in feature/sucompat.c,
 * which is why that one is called ksu_sucompat_execveat.
 */
int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    (void)fd;
    (void)filename_ptr;
    (void)argv;
    (void)envp;
    (void)flags;
    return 1;
}

/*
 * Post-exec hook. This tree handles the result inside its own execveat path and
 * has no equivalent work to do after the kernel call returns.
 */
int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags,
                                      int *retval)
{
    (void)fd;
    (void)filename_ptr;
    (void)argv;
    (void)envp;
    (void)flags;
    (void)retval;
    return 0;
}

/*
 * SYSCALL_DEFINE4(reboot) calls this before the capability check, giving the
 * manager a chance to reboot the device through the su fd. This tree grants that
 * by letting the manager's uid pass the capability check in the first place, so
 * no interception is needed here.
 */
int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    (void)magic1;
    (void)magic2;
    (void)cmd;
    (void)arg;
    return 0;
}

#endif // #ifdef CONFIG_KSU_SUSFS

/*
 * Without the patch there is nothing to adapt and every definition above is
 * compiled out. Keep one symbol so the object is not empty - an empty .o is a
 * warning on some toolchains and a link-order surprise on others - and mark it
 * __maybe_unused so the compiler does not object to a static nothing refers to.
 */
#ifndef CONFIG_KSU_SUSFS
static int ksu_susfs_hooks_unused __maybe_unused;
#endif
