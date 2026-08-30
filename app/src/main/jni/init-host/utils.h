#ifndef UTILS_H
#define UTILS_H

int skip_dir(const char *name);
const char *find_su_path(void);
void extract_rootfs(const char *rootfs_path, const char *dest);
int distribution_run_command(const char *command);
int mkdir_p(const char *path);

#endif // UTILS_H