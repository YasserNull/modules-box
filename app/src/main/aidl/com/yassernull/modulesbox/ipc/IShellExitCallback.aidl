package com.yassernull.modulesbox.ipc;

interface IShellExitCallback {
    void onShellExit(int pid, int exitCode);
}
