#ifndef GLOBALS_H
#define GLOBALS_H

#define MAX_PATH 256
#define MAX_ARGS 8192
#define MAX_PATH_LEN 256

extern char linker[MAX_PATH];
extern char prefix[MAX_PATH];
extern char local[MAX_PATH];
extern char proot_args[MAX_ARGS];

#define DISTRIBUTION_PATH local, "distribution"
#define ANDROID_PATH "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/apex/com.android.virt/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/sbin"

#endif