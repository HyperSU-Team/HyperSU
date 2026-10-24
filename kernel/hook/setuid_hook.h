#ifndef __KSU_H_KSU_CORE
#define __KSU_H_KSU_CORE

#include <linux/init.h>
#include <linux/types.h>

void ksu_setuid_hook_init(void);
void ksu_setuid_hook_exit(void);

/*
 * Handler functions for hook_manager.
 *
 * The three-argument form is the one the SUSFS kernel patch declares in
 * kernel/sys.c, so it is the definition the kernel links against. Callers inside
 * this tree that only have an old and a new uid pass the new one for both euid
 * and suid, which is what Android's zygote does anyway.
 */
int ksu_handle_setresuid(uid_t ruid, uid_t euid, uid_t suid);

#endif
