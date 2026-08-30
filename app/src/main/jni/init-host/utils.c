#include "utils.h"
#include "globals.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
int skip_dir(const char *name) {
  const char *skip[] = {"bin",   "boot", "etc",  "home",         "lib",
                        "media", "opt",  "root", "run",          "sbin",
                        "srv",   "usr",  "var",  "linkerconfig", NULL};

  for (int i = 0; skip[i] != NULL; i++) {
    if (strcmp(name, skip[i]) == 0)
      return 1;
  }

  return 0;
}

#include <sys/stat.h>

const char *find_su_path() {
  static char su_command[256];
  const char *paths[] = {"/bin/su", "/usr/bin/su", "/usr/local/bin/su"};
  struct stat st;

  for (int i = 0; i < 3; i++) {
    char full_path[512];
    snprintf(full_path, sizeof(full_path), "%s/%s%s", DISTRIBUTION_PATH, paths[i]);
    
    // lstat تتحقق من الرابط نفسه وليس ما يشير إليه
    if (lstat(full_path, &st) == 0) {
      
      strncpy(su_command, paths[i], sizeof(su_command));
      su_command[sizeof(su_command) - 1] = '\0';
      return su_command;
    }
  }

  strncpy(su_command, "/bin/sh", sizeof(su_command));
  su_command[sizeof(su_command) - 1] = '\0';
  return su_command;
}

void extract_rootfs(const char *rootfs_path, const char *dest) {
  char cmd[1024];
  char line[512];
  // Construct the final extraction command based on our findings
      snprintf(cmd, sizeof(cmd),
               "%s %s/bin/busybox tar -xzvf \"%s\" -C \"%s\"",
               linker,local,rootfs_path, dest);
    
  system(cmd);
}

int distribution_run_command(const char *command) {
  char cmd[10240];
  // proot لا يقبل -c؛ نشغّل الأمر عبر /bin/sh داخل الـ rootfs.
  const char *su_cmd = find_su_path();
  snprintf(cmd, sizeof(cmd), "%s %s/bin/proot %s %s -c \"%s\"", linker,
           local, proot_args, su_cmd,command);
  
  
  return system(cmd);
}

/* Recursive mkdir -p (creates parent directories as needed). */
int mkdir_p(const char *path) {
  char tmp[PATH_MAX];
  struct stat st;
  char *p;

  snprintf(tmp, sizeof(tmp), "%s", path);
  /* Remove trailing slash if present */
  size_t len = strlen(tmp);
  if (len > 0 && tmp[len - 1] == '/')
    tmp[len - 1] = '\0';

  for (p = tmp + 1; *p; p++) {
    if (*p == '/') {
      *p = '\0';
      if (stat(tmp, &st) != 0) {
        if (mkdir(tmp, 0755) != 0)
          return -1;
      }
      *p = '/';
    }
  }
  if (stat(tmp, &st) != 0) {
    if (mkdir(tmp, 0755) != 0)
      return -1;
  }
  return 0;
}
