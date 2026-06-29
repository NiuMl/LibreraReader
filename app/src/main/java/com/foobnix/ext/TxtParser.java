package com.foobnix.ext;

import android.util.Log;

import java.io.Closeable;
import java.io.File;

public class TxtParser implements Closeable {
    private static final String TAG = "TxtParser";
    private static boolean libraryLoaded = false;

    static {
        try {
            System.loadLibrary("txtparser");
            libraryLoaded = true;
            Log.d(TAG, "Successfully loaded txtparser library");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load txtparser library: " + e.getMessage());
            libraryLoaded = false;
        }
    }

    public static boolean isLibraryAvailable() {
        return libraryLoaded;
    }

    private long nativeHandle;

    public TxtParser() {
        this.nativeHandle = 0;
    }

    public boolean open(String filepath) {
        if (!libraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return false;
        }
        return nativeOpen(filepath);
    }

    public boolean open(File file) {
        return open(file.getAbsolutePath());
    }

    @Override
    public void close() {
        if (nativeHandle != 0) {
            nativeClose();
            nativeHandle = 0;
        }
    }

    public int getSectionCount() {
        if (!libraryLoaded || nativeHandle == 0) return 0;
        return nativeGetSectionCount();
    }

    public String getSectionName(int index) {
        if (!libraryLoaded || nativeHandle == 0) return null;
        return nativeGetSectionName(index);
    }

    public String getTitle() {
        if (!libraryLoaded || nativeHandle == 0) return null;
        return nativeGetTitle();
    }

    public int getEncoding() {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeGetEncoding();
    }

    public long getFileSize() {
        if (!libraryLoaded || nativeHandle == 0) return 0;
        return nativeGetFileSize();
    }

    public int extractToHtml(String outputPath) {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeExtractToHtml(outputPath);
    }

    public int extractToEpub(String outputPath) {
        if (!libraryLoaded || nativeHandle == 0) return -1;
        return nativeExtractToEpub(outputPath);
    }

    public void setSectionPattern(String pattern) {
        if (libraryLoaded && nativeHandle != 0) {
            nativeSetSectionPattern(pattern);
        }
    }

    private native boolean nativeOpen(String filepath);
    private native void nativeClose();
    private native int nativeGetSectionCount();
    private native String nativeGetSectionName(int index);
    private native String nativeGetTitle();
    private native int nativeGetEncoding();
    private native long nativeGetFileSize();
    private native int nativeExtractToHtml(String outputPath);
    private native int nativeExtractToEpub(String outputPath);
    private native void nativeSetSectionPattern(String pattern);
}