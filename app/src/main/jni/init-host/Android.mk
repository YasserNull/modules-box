LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := init-host
LOCAL_SRC_FILES := init-host.c globals.c init_proot.c utils.c
LOCAL_MODULE_CLASS := EXECUTABLES
include $(BUILD_EXECUTABLE)
