#include <linux/compiler.h>
#include <linux/version.h>
#include <linux/slab.h>
#include <linux/task_work.h>
#include <linux/thread_info.h>
#include <linux/seccomp.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/uidgid.h>

#include "policy/allowlist.h"
#include "hook/setuid_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "infra/seccomp_cache.h"
#include "supercall/supercall.h"
#include "hook/tp_marker.h"
#include "feature/kernel_umount.h"
#include "feature/mount_hide.h"

/*
 * Signature must match the SUSFS kernel patch, which declares
 *
 *     extern int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid);
 *
 * in kernel/sys.c and calls it from SYSCALL_DEFINE3(setresuid) *before*
 * __sys_setresuid() runs.
 *
 * The three arguments are the values being requested, not the current identity:
 * setresuid(1000, 1000, 1000) asks for all three to become 1000. So the identity
 * being left behind has to be read from current, which still holds it at this
 * point in the syscall. Taking ruid as the old uid - as this function used to -
 * yields old == new and breaks ksu_handle_umount(), which needs to know what the
 * process was before the transition.
 */
int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid)
{
    uid_t old_uid = current_uid().val;
    uid_t new_uid = euid;

    (void)ruid;
    (void)suid;

    // we rely on the fact that zygote always call setresuid(3) with same uids

    pr_info("handle_setresuid from %d to %d\n", old_uid, new_uid);

    if (unlikely(is_uid_manager(new_uid))) {
        spin_lock_irq(&current->sighand->siglock);
        ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
        ksu_set_task_tracepoint_flag(current);
        spin_unlock_irq(&current->sighand->siglock);

        pr_info("install fd for manager: %d\n", new_uid);
        ksu_install_fd();
        return 0;
    }

    if (ksu_is_allow_uid_for_current(new_uid)) {
        if (current->seccomp.mode == SECCOMP_MODE_FILTER && current->seccomp.filter) {
            spin_lock_irq(&current->sighand->siglock);
            ksu_seccomp_allow_cache(current->seccomp.filter, __NR_reboot);
            spin_unlock_irq(&current->sighand->siglock);
        }
        ksu_set_task_tracepoint_flag(current);
    } else {
        ksu_clear_task_tracepoint_flag_if_needed(current);
    }

    // Handle kernel umount
    ksu_handle_umount(old_uid, new_uid);

    return 0;
}

void __init ksu_setuid_hook_init(void)
{
    ksu_kernel_umount_init();
    ksu_mount_hide_init();
}

void __exit ksu_setuid_hook_exit(void)
{
    pr_info("ksu_core_exit\n");
    ksu_kernel_umount_exit();
    ksu_mount_hide_exit();
}
