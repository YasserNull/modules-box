package com.termux.terminal;

public final class PtyProcess {
  private PtyProcess() {}

  public static int createSubprocess(
      String shellPath,
      String cwd,
      String[] args,
      String[] env,
      int[] processId,
      int rows,
      int cols,
      int cellWidth,
      int cellHeight) {
    return JNI.createSubprocess(
        shellPath, cwd, args, env, processId, rows, cols, cellWidth, cellHeight);
  }

  public static int waitFor(int pid) {
    return JNI.waitFor(pid);
  }

  public static void setPtyWindowSize(int fd, int rows, int cols, int cellWidth, int cellHeight) {
    JNI.setPtyWindowSize(fd, rows, cols, cellWidth, cellHeight);
  }

  public static void close(int fd) {
    JNI.close(fd);
  }
}
