#include "init_proot.h"
#include "globals.h"
#include "utils.h"
#include <dirent.h>
#include <grp.h>
#include <pwd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
void add_proot_arg(const char *arg) {
  strcat(proot_args, " ");
  strcat(proot_args, arg);
}


void init_proot_args() {
  add_proot_arg("-w /");

  char buf[MAX_PATH];

  snprintf(buf, sizeof(buf), "-b %s", prefix);
  add_proot_arg(buf);

    add_proot_arg("--kill-on-exit");

    add_proot_arg("--root-id");
  
    add_proot_arg("--link2symlink");
  
    add_proot_arg("-b /dev -b /sys -b /proc -b /system -b /apex -b /cust -b /product -b /data -b /vendor -b /system_ext -b /odm -b /sdcard -b /storage");
    
    

  add_proot_arg("-b /dev/urandom:/dev/random");

  
  if (access("/proc/self/fd", F_OK) == 0)
    add_proot_arg("-b /proc/self/fd:/dev/fd");
  if (access("/proc/self/fd/0", F_OK) == 0)
    add_proot_arg("-b /proc/self/fd/0:/dev/stdin");
  if (access("/proc/self/fd/1", F_OK) == 0)
    add_proot_arg("-b /proc/self/fd/1:/dev/stdout");
  if (access("/proc/self/fd/2", F_OK) == 0)
    add_proot_arg("-b /proc/self/fd/2:/dev/stderr");

  snprintf(buf, sizeof(buf), "-b %s/distribution/tmp:/dev/shm", local);
  add_proot_arg(buf);

  snprintf(buf, sizeof(buf), "-r %s/distribution", local);
  add_proot_arg(buf);
}

int init_proot(int argc, char *argv[]) {
  pid_t pid;
  int status;
  const char *su_cmd = find_su_path();

  pid = fork();
  if (pid == -1) {
    perror("fork");
    return -1;
  }

  if (pid == 0) {
    // Kill proot if init-host dies, so killing the terminal session's shell process
    // also stops the guest instead of leaving it orphaned.
    prctl(PR_SET_PDEATHSIG, SIGKILL);
    if (getppid() == 1) {
      _exit(1);
    }

    // بناء argv لـ proot
    char *proot_argv[64];
    int i = 0;

    proot_argv[i++] = (char *)linker;

    // proot binary
    char proot_bin[512];
    snprintf(proot_bin, sizeof(proot_bin), "%s/bin/proot", local);
    proot_argv[i++] = proot_bin;

    // proot arguments

    char args_copy[4096];
    strncpy(args_copy, proot_args, sizeof(args_copy) - 1);
    args_copy[sizeof(args_copy) - 1] = '\0';

    char *token = strtok(args_copy, " ");
    while (token != NULL && i < 60) {
      proot_argv[i++] = token;
      token = strtok(NULL, " ");
    }

    // بناء أمر shell. تبقى rootfs مضبوطة عبر "-r" في proot_args، لذا لا نضيف
    // argv[1] ("proot") كأمر — كان ذلك يسبب محاولة تشغيل proot نفسه.
    char shell_cmd[4096];

    if (su_cmd != NULL && su_cmd[0]) {
      // مع SU داخل التوزيعة
      if (argc <= 2) {
        snprintf(shell_cmd, sizeof(shell_cmd),
                 ". /etc/profile; "
                 "case \":$PATH:\" in *:/product/bin:*) ;; *) export PATH=\"$PATH:%s\";; esac; "
                 "cd $HOME; exec %s",
                 ANDROID_PATH,
                 su_cmd);
      } else {
        // جمع arguments من argv[2] فما بعد
        char args[2048] = "";
        for (int j = 2; j < argc; j++) {
          strcat(args, argv[j]);
          if (j < argc - 1)
            strcat(args, " ");
        }

        snprintf(shell_cmd, sizeof(shell_cmd),
                 ". /etc/profile; "
                 "case \":$PATH:\" in *:/product/bin:*) ;; *) export PATH=\"$PATH:%s\";; esac; "
                 "cd $HOME; exec %s -c '%s'",
                 ANDROID_PATH,
                 su_cmd, args);
      }
    } else {
      if (argc <= 2) {
        snprintf(shell_cmd, sizeof(shell_cmd),
                 ". /etc/profile; "
                 "case \":$PATH:\" in *:/product/bin:*) ;; *) export PATH=\"$PATH:%s\";; esac; "
                 "cd $HOME; exec /bin/sh",
                 ANDROID_PATH);
      } else {
        char args[2048] = "";
        for (int j = 2; j < argc; j++) {
          strcat(args, argv[j]);
          if (j < argc - 1)
            strcat(args, " ");
        }

        snprintf(shell_cmd, sizeof(shell_cmd),
                 ". /etc/profile; "
                 "case \":$PATH:\" in *:/product/bin:*) ;; *) export PATH=\"$PATH:%s\";; esac; "
                 "cd $HOME; exec /bin/sh -c '%s'",
                 ANDROID_PATH,
                 args);
      }
    } 
    // proot لا يقبل -c؛ نشغّل أمر shell عبر /bin/sh داخل الـ rootfs.
    proot_argv[i++] = "/bin/sh";
    proot_argv[i++] = "-c";
    proot_argv[i++] = shell_cmd;
    proot_argv[i] = NULL;

    // تأكد من وجود أدلة proot المؤقتة قبل تشغيله (PROOT_TMP_DIR / TMPDIR).
    const char *proot_tmp_dir = getenv("PROOT_TMP_DIR");
    if (proot_tmp_dir != NULL && proot_tmp_dir[0] != '\0') {
      mkdir_p(proot_tmp_dir);
    }
    const char *tmpdir = getenv("TMPDIR");
    if (tmpdir != NULL && tmpdir[0] != '\0') {
      mkdir_p(tmpdir);
    }

    // تنفيذ proot
   
// طباعة في سطر واحد

    execvp(proot_argv[0], proot_argv);
    
    perror("execvp proot");
    exit(1);
  }

  // Parent ينتظر
  if (waitpid(pid, &status, 0) == -1) {
    perror("waitpid");
    return -1;
  }

  if (WIFEXITED(status))
    return WEXITSTATUS(status);
  else if (WIFSIGNALED(status))
    return 128 + WTERMSIG(status);
  else
    return -1;
}
