#include "globals.h"
#include "init_proot.h"
#include "utils.h"
#include <dirent.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

void fix_resolv_conf() {

  char resolv_path[MAX_PATH];
  char temp_path[MAX_PATH];
  struct stat st;

  snprintf(resolv_path, MAX_PATH, "%s/%s/etc/resolv.conf", DISTRIBUTION_PATH);

  int exists = (lstat(resolv_path, &st) == 0);
  int is_broken_link = (S_ISLNK(st.st_mode) && stat(resolv_path, &st) != 0);
  int is_empty = (S_ISREG(st.st_mode) && st.st_size == 0);

  if (!exists || is_broken_link || is_empty) {
    unlink(resolv_path);
    snprintf(temp_path, MAX_PATH, "%s/%s/run/systemd", DISTRIBUTION_PATH);

    if (access(temp_path, F_OK) == 0) {
      char stub_file[MAX_PATH];
      snprintf(temp_path, MAX_PATH, "%s/%s/run/systemd/resolve",
               DISTRIBUTION_PATH);
      snprintf(stub_file, MAX_PATH,
               "%s/%s/run/systemd/resolve/stub-resolv.conf", DISTRIBUTION_PATH);

      char cmd[MAX_PATH + 10];
      snprintf(cmd, sizeof(cmd), "mkdir -p %s", temp_path);
      system(cmd);

      FILE *f = fopen(stub_file, "w");
      if (f) {
        fprintf(f, "nameserver 8.8.8.8\nnameserver 8.8.4.4\n");
        fclose(f);
      }
      symlink(stub_file, resolv_path);
    } else {
      FILE *f = fopen(resolv_path, "w");
      if (f) {
        fprintf(f, "nameserver 8.8.8.8\nnameserver 8.8.4.4\n");
        fclose(f);
      }
    }
  }
}

void fix_timezone() {
  char zoneinfo_path[MAX_PATH];
  char timezone[256] = {0};
  struct stat st;

  snprintf(zoneinfo_path, MAX_PATH, "%s/%s/usr/share/zoneinfo",
           DISTRIBUTION_PATH);
  if (stat(zoneinfo_path, &st) != 0 || !S_ISDIR(st.st_mode)) {
    return;
  }

  FILE *fp = popen("getprop persist.sys.timezone 2>/dev/null", "r");
  if (fp) {
    if (fgets(timezone, sizeof(timezone), fp)) {
      timezone[strcspn(timezone, "\n\r")] = 0;
    }
    pclose(fp);
  }

  if (strlen(timezone) == 0) {
    char etc_timezone[MAX_PATH];
    snprintf(etc_timezone, MAX_PATH, "%s/%s/etc/timezone", DISTRIBUTION_PATH);
    FILE *f = fopen(etc_timezone, "r");
    if (f) {
      if (fgets(timezone, sizeof(timezone), f)) {
        timezone[strcspn(timezone, "\n\r")] = 0;
      }
      fclose(f);
    }
  }
  if (strlen(timezone) == 0) {
    strcpy(timezone, "Etc/UTC");
  }
  char target_zone_file[MAX_PATH];
  snprintf(target_zone_file, MAX_PATH, "%s/%s/usr/share/zoneinfo/%s",
           DISTRIBUTION_PATH, timezone);

  if (stat(target_zone_file, &st) == 0) {
    char localtime_path[MAX_PATH];
    snprintf(localtime_path, MAX_PATH, "%s/%s/etc/localtime",
             DISTRIBUTION_PATH);
    unlink(localtime_path);
  }
}

void fix_linker_config() {

  char linker_dir[MAX_PATH];
  char ld_config_path[MAX_PATH];
  struct stat st;

  snprintf(linker_dir, MAX_PATH, "%s/%s/linkerconfig", DISTRIBUTION_PATH);
  snprintf(ld_config_path, MAX_PATH, "%s/%s/linkerconfig/ld.config.txt",
           DISTRIBUTION_PATH);

  if (stat(ld_config_path, &st) != 0) {
    char mkdir_cmd[MAX_PATH + 10];
    snprintf(mkdir_cmd, sizeof(mkdir_cmd), "mkdir -p %s", linker_dir);
    system(mkdir_cmd);

    FILE *f = fopen(ld_config_path, "a");
    if (f) {
      fclose(f);
    }
  }
}

void fix_groups_and_permissions() {
    char group_file_path[MAX_PATH];
    snprintf(group_file_path, MAX_PATH, "%s/%s/etc/group", DISTRIBUTION_PATH);

    // ============ 1. التحقق من وجود المجموعات النصية وإضافتها ============
    const char *text_groups[] = {
        "aid_inet:x:3003:",  "aid_net_raw:x:3004:", "aid_graphics:x:1003:",
        "aid_input:x:1004:", "aid_audio:x:1005:",   "aid_video:x:1006:",
        "aid_drm:x:1007:"
    };
    int num_text = sizeof(text_groups) / sizeof(text_groups[0]);
    int exists[num_text];
    memset(exists, 0, sizeof(exists));

    // قراءة الملف مرة واحدة
    FILE *f = fopen(group_file_path, "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            for (int i = 0; i < num_text; i++) {
                if (strstr(line, text_groups[i])) exists[i] = 1;
            }
        }
        fclose(f);
    }

    // إضافة المفقود
    f = fopen(group_file_path, "a");
    if (f) {
        for (int i = 0; i < num_text; i++) {
            if (!exists[i]) fprintf(f, "%s\n", text_groups[i]);
        }
        fclose(f);
    }

    // ============ 2. التحقق من المجموعات الرقمية بدون أوامر ============
    int gids[] = {1077, 3003, 9997, 20412, 50412, 99909997};
    int num_gids = sizeof(gids) / sizeof(gids[0]);
    int gid_exists[num_gids];
    memset(gid_exists, 0, sizeof(gid_exists));

    // قراءة الملف مرة أخرى للبحث عن المجموعات الرقمية
    f = fopen(group_file_path, "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            for (int i = 0; i < num_gids; i++) {
                // البحث عن ":x:GID:" في السطر
                char pattern[32];
                snprintf(pattern, sizeof(pattern), ":x:%d:", gids[i]);
                if (strstr(line, pattern)) {
                    gid_exists[i] = 1;
                }
            }
        }
        fclose(f);
    }

    // إضافة المجموعات الرقمية المفقودة مباشرة للملف
    f = fopen(group_file_path, "a");
    if (f) {
        for (int i = 0; i < num_gids; i++) {
            if (!gid_exists[i]) {
                fprintf(f, "unknown_%d:x:%d:\n", gids[i], gids[i]);
            }
        }
        fclose(f);
    }
}
void fix_profile() {
  char profile_d[MAX_PATH];
  char path_script[MAX_PATH];

  snprintf(profile_d, MAX_PATH, "%s/%s/etc/profile.d", DISTRIBUTION_PATH);
  mkdir_p(profile_d);

  snprintf(path_script, MAX_PATH, "%s/%s/etc/profile.d/00-android-path.sh", DISTRIBUTION_PATH);
  FILE *f = fopen(path_script, "w");
  if (f) {
    fprintf(f,
            "case \":$PATH:\" in\n"
            "  *:/product/bin:*)\n"
            "    ;;\n"
            "  *)\n"
            "    export PATH=\"$PATH:%s\"\n"
            "    ;;\n"
            "esac\n",
            ANDROID_PATH);
    fclose(f);
  }
}

void init_distribution(const char *local, const char *distribution_dir) {
  DIR *dir = opendir(distribution_dir);
  if (!dir)
    return;

  struct dirent *entry;
  int empty = 1;

  while ((entry = readdir(dir))) {

    if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0)
      continue;
    if (strcmp(entry->d_name, "root") == 0 || strcmp(entry->d_name, "tmp") == 0)
      continue;

    empty = 0;
    break;
  }

  closedir(dir);

  // Alpine minirootfs archive.
  char rootfs_path[512];
  snprintf(rootfs_path, sizeof(rootfs_path), "%s/distribution.tar.gz", local);

  int need_extract = empty;
  if (!empty) {
    // Interrupted extraction leftovers: re-extract when the Alpine marker is missing.
    char marker_path[1024];
    snprintf(marker_path, sizeof(marker_path), "%s/etc/alpine-release", distribution_dir);
    if (access(marker_path, F_OK) != 0)
      need_extract = 1;
  }

  if (need_extract) {
    if (access(rootfs_path, F_OK) != 0) {
      printf("distribution archive not found.\n");
      exit(1);
    }
    if (!empty) {
      // Stale rootfs (interrupted extraction or previous distro): wipe the dir
      // contents, then recreate it plus the runtime dirs sessions need (tmp/ for
      // /dev/shm, root/ as shell home and module staging home).
      char cmd[1024];
      snprintf(cmd, sizeof(cmd), "rm -rf '%s'", distribution_dir);
      system(cmd);
      mkdir_p(distribution_dir);
      char tmp_path[1024], root_path[1024];
      snprintf(tmp_path, sizeof(tmp_path), "%s/tmp", distribution_dir);
      snprintf(root_path, sizeof(root_path), "%s/root", distribution_dir);
      mkdir_p(tmp_path);
      mkdir_p(root_path);
    }
    extract_rootfs(rootfs_path, distribution_dir);
  }
  
  fix_linker_config();
  
  fix_resolv_conf();
 
  fix_timezone();
  fix_groups_and_permissions();
  fix_profile();
}

int main(int argc, char *argv[]) {
  char *linker_env = getenv("LINKER");
  char *prefix_env = getenv("PREFIX");

  if (linker_env == NULL) {
    printf("LINKER not set\n");
    return 1;
  }

  if (prefix_env == NULL) {
    printf("PREFIX not set\n");
    return 1;
  }

  strncpy(linker, linker_env, sizeof(linker) - 1);
  linker[sizeof(linker) - 1] = '\0';

  strncpy(prefix, prefix_env, sizeof(prefix) - 1);
  prefix[sizeof(prefix) - 1] = '\0';

  snprintf(local, sizeof(local), "%s/local", prefix);
  
  char resolved[PATH_MAX];

  if (realpath(argv[0], resolved)) {
    if (strcmp(resolved, "/data/local/tmp/modules-box/bin/init-host") == 0) {
      snprintf(prefix, sizeof(prefix), "/data/local/tmp/modules-box");
      snprintf(local, sizeof(local), "%s", prefix);

    } else if (strcmp(resolved, "/data/local/tmp/modules-box/bin/init-host") == 0) {
      snprintf(prefix, sizeof(prefix), "/data/local/tmp");
      snprintf(local, sizeof(local), "%s/modules-box", prefix);
    }
  } else {
    return 1;
  }

  char distribution_dir[PATH_MAX];
  snprintf(distribution_dir, sizeof(distribution_dir), "%s/distribution",
           local);

  mkdir_p(distribution_dir);

  init_proot_args();
  init_distribution(local, distribution_dir);
  
 return init_proot(argc, argv);

  return 0;
}
