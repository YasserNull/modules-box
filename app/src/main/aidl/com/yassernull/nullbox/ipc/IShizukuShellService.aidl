package com.yassernull.nullbox.ipc;

import com.yassernull.nullbox.ipc.PtyInfo;
import com.yassernull.nullbox.ipc.IShellExitCallback;

interface IShizukuShellService {
    PtyInfo startShell(String shellPath, String cwd, in String[] args, in String[] env,
        int rows, int cols, int cellWidth, int cellHeight, IShellExitCallback callback);
    void stopShell(int pid);
}
