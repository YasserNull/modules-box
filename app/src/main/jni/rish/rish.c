#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>

static void usage() {
    fprintf(stderr, "Usage: rish -c \"command\"\n");
}

static int file_exists(const char *path) {
    struct stat st;
    return stat(path, &st) == 0;
}

int main(int argc, char **argv) {
    if (argc < 3 || strcmp(argv[1], "-c") != 0) {
        usage();
        return 2;
    }

    const char *cmd = argv[2];
    if (cmd == NULL || cmd[0] == '\0') {
        fprintf(stderr, "rish: empty command\n");
        return 2;
    }

    pid_t pid = getpid();
    const char *base_dir = "/data/data/com.yassernull.modulesbox/tmp";
    char base[512];
    snprintf(base, sizeof(base), "%s/rish-%d", base_dir, (int)pid);

    char cmd_path[600];
    char out_path[600];
    char err_path[600];
    char code_path[600];
    char req_path[600];
    char ready_path[600];
    snprintf(cmd_path, sizeof(cmd_path), "%s.cmd", base);
    snprintf(out_path, sizeof(out_path), "%s.out", base);
    snprintf(err_path, sizeof(err_path), "%s.err", base);
    snprintf(code_path, sizeof(code_path), "%s.code", base);
    snprintf(req_path, sizeof(req_path), "%s.req", base);
    snprintf(ready_path, sizeof(ready_path), "%s/rish.ready", base_dir);

    if (mkdir(base_dir, 0700) != 0 && errno != EEXIST) {
        fprintf(stderr, "rish: cannot create base dir (%s): %s\n", base_dir, strerror(errno));
        return 1;
    }

    if (!file_exists(ready_path)) {
        fprintf(stderr, "rish: daemon not running\n");
        return 125;
    }

    // Write command to file
    FILE *fcmd = fopen(cmd_path, "w");
    if (!fcmd) {
        fprintf(stderr, "rish: cannot write command file (%s)\n", strerror(errno));
        return 1;
    }
    fputs(cmd, fcmd);
    fclose(fcmd);

    // Write request file
    FILE *freq = fopen(req_path, "w");
    if (!freq) {
        fprintf(stderr, "rish: cannot write request file (%s)\n", strerror(errno));
        return 1;
    }
    fprintf(freq, "%s\n%s\n%s\n%s\n", cmd_path, out_path, err_path, code_path);
    fclose(freq);

    // Wait for code file
    int waited_ms = 0;
    while (!file_exists(code_path)) {
        usleep(50000);
        waited_ms += 50;
        if (waited_ms > 60000) {
            fprintf(stderr, "rish: timeout waiting for result\n");
            return 124;
        }
    }

    // Print stdout
    FILE *fout = fopen(out_path, "r");
    if (fout) {
        char buf[4096];
        size_t n;
        while ((n = fread(buf, 1, sizeof(buf), fout)) > 0) {
            fwrite(buf, 1, n, stdout);
        }
        fclose(fout);
    }

    // Print stderr
    FILE *ferr = fopen(err_path, "r");
    if (ferr) {
        char buf[4096];
        size_t n;
        while ((n = fread(buf, 1, sizeof(buf), ferr)) > 0) {
            fwrite(buf, 1, n, stderr);
        }
        fclose(ferr);
    }

    // Read exit code
    int exit_code = 0;
    FILE *fcode = fopen(code_path, "r");
    if (fcode) {
        if (fscanf(fcode, "%d", &exit_code) != 1) {
            exit_code = 0;
        }
        fclose(fcode);
    }

    // Cleanup
    remove(req_path);
    remove(cmd_path);
    remove(out_path);
    remove(err_path);
    remove(code_path);

    return exit_code;
}
