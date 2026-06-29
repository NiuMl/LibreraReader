LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
LOCAL_ARM_NEON := true

LOCAL_MODULE := txtparser

LOCAL_SRC_FILES := \
    txtparser.cpp \
    txtparser_jni.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)

LOCAL_CFLAGS := -O3 -DNDEBUG
LOCAL_CPPFLAGS := -O3 -DNDEBUG

LOCAL_LDLIBS := -llog -lz

include $(BUILD_STATIC_LIBRARY)