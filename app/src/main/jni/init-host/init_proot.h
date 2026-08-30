#ifndef INIT_PROOT_H
#define INIT_PROOT_H

void add_proot_arg(const char *arg);
void bind_android_dirs(void);
void init_proot_args(void);
int init_proot(int argc, char *argv[]);

#endif