#include <linux/version.h>
#include <linux/fs.h>
#include <linux/nsproxy.h>
#include <linux/sched/task.h>
#include <linux/uaccess.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include "klog.h" // IWYU pragma: keep
#include "infra/seccomp_cache.h"

/*
 * The action cache this file pokes at was added in 5.9 (0eebfedec05d): a bitmap
 * of syscalls already known to return SECCOMP_RET_ALLOW, checked in
 * seccomp_run_filters() before the BPF programs run. It exists purely to skip
 * filter evaluation.
 *
 * Before that, seccomp_run_filters() always runs every program in the chain and
 * struct seccomp_filter is just { usage, log, prev, prog } - there is no bitmap
 * to set and no place a "this syscall is allowed" hint could be stored. Writing
 * to one is not merely unnecessary on those kernels, it is impossible.
 *
 * So on kernels without the cache both entry points are no-ops, and callers
 * keep their unconditional call sites. The su domain reaches reboot through the
 * normal path there.
 */
#ifdef KSU_COMPAT_HAS_SECCOMP_ACTION_CACHE

struct action_cache {
    DECLARE_BITMAP(allow_native, SECCOMP_ARCH_NATIVE_NR);
#ifdef SECCOMP_ARCH_COMPAT
    DECLARE_BITMAP(allow_compat, SECCOMP_ARCH_COMPAT_NR);
#endif
};

/*
 * This mirrors the kernel's private struct seccomp_filter. It is only ever used
 * to reach ->cache, so the fields before it must match the kernel's layout
 * exactly; the ones after do not matter and are omitted.
 */
struct seccomp_filter {
    refcount_t refs;
    refcount_t users;
    bool log;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
    bool wait_killable_recv;
#endif
    struct action_cache cache;
    struct seccomp_filter *prev;
    struct bpf_prog *prog;
    struct notification *notif;
    struct mutex notify_lock;
    wait_queue_head_t wqh;
};

void ksu_seccomp_clear_cache(struct seccomp_filter *filter, int nr)
{
    if (!filter) {
        return;
    }

    if (nr >= 0 && nr < SECCOMP_ARCH_NATIVE_NR) {
        clear_bit(nr, filter->cache.allow_native);
    }

#ifdef SECCOMP_ARCH_COMPAT
    if (nr >= 0 && nr < SECCOMP_ARCH_COMPAT_NR) {
        clear_bit(nr, filter->cache.allow_compat);
    }
#endif
}

void ksu_seccomp_allow_cache(struct seccomp_filter *filter, int nr)
{
    if (!filter) {
        return;
    }

    if (nr >= 0 && nr < SECCOMP_ARCH_NATIVE_NR) {
        set_bit(nr, filter->cache.allow_native);
    }

#ifdef SECCOMP_ARCH_COMPAT
    if (nr >= 0 && nr < SECCOMP_ARCH_COMPAT_NR) {
        set_bit(nr, filter->cache.allow_compat);
    }
#endif
}

#else // #ifdef KSU_COMPAT_HAS_SECCOMP_ACTION_CACHE

/*
 * Kernels older than 5.9. struct seccomp_filter is opaque from here and has no
 * cache member, so there is nothing to clear or set. Keeping the functions
 * defined rather than making the callers conditional means hook/setuid_hook.c
 * stays identical across kernels.
 */
void ksu_seccomp_clear_cache(struct seccomp_filter *filter, int nr)
{
    (void)filter;
    (void)nr;
}

void ksu_seccomp_allow_cache(struct seccomp_filter *filter, int nr)
{
    (void)filter;
    (void)nr;
}

#endif // #ifdef KSU_COMPAT_HAS_SECCOMP_ACTION_CACHE
