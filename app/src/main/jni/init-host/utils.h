#ifndef UTILS_H
#define UTILS_H


const char *find_su_path(void);
void extract_rootfs(const char *rootfs_path, const char *dest);
int mkdir_p(const char *path);

#endif // UTILS_H