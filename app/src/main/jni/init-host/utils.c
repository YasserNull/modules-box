#include "utils.h"
#include "globals.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

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
  // Alpine ships .tar.gz (gzip).
  // Construct the final extraction command based on our findings
      snprintf(cmd, sizeof(cmd),
               "%s %s/bin/busybox tar -xzvf \"%s\" --strip-components=1 -C \"%s\"",
               linker,local,rootfs_path, dest);
    
  system(cmd);
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
