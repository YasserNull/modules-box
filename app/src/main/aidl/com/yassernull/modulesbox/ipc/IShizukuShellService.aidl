package com.yassernull.modulesbox.ipc;

import com.yassernull.modulesbox.ipc.PtyInfo;
import com.yassernull.modulesbox.ipc.IShellExitCallback;

interface IShizukuShellService {
    PtyInfo startShell(String shellPath, String cwd, in String[] args, in String[] env,
        int rows, int cols, int cellWidth, int cellHeight, IShellExitCallback callback);
    void stopShell(int pid);
    void setAppPid(int pid);
    void shutdown();
}
