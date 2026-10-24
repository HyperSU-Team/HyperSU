#ifndef __KSU_H_SUCOMPAT
#define __KSU_H_SUCOMPAT
#include <asm/ptrace.h>
#include <linux/jump_label.h>
#include <linux/types.h>

/*
 * Shared with the SUSFS kernel patch, which declares this exact symbol in
 * fs/exec.c, fs/open.c and fs/stat.c as a static_key_true and tests it with
 * static_branch_likely(). The type is not negotiable: a bool here would link and
 * then be read as a jump-table entry.
 */
extern struct static_key_true ksu_su_compat_enabled;

bool ksu_su_compat_is_enabled(void);

void ksu_sucompat_init(void);
void ksu_sucompat_exit(void);

// Handler functions exported for hook_manager
long ksu_handle_faccessat_sucompat(int orig_nr, struct pt_regs *regs);
long ksu_handle_stat_sucompat(int orig_nr, struct pt_regs *regs);
long ksu_handle_execve_sucompat(const char __user **filename_user, int orig_nr, struct pt_regs *regs);
long ksu_sucompat_execveat(const char __user **filename_user, int orig_nr, struct pt_regs *regs);

#endif
