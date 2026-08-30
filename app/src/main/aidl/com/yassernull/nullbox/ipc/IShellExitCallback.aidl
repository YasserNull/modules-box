package com.yassernull.nullbox.ipc;

interface IShellExitCallback {
    void onShellExit(int pid, int exitCode);
}
