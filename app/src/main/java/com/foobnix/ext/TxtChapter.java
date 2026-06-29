package com.foobnix.ext;

public class TxtChapter {
    public String title;
    public long start;
    public long end;
    
    public TxtChapter() {
    }
    
    public TxtChapter(String title, long start, long end) {
        this.title = title;
        this.start = start;
        this.end = end;
    }
}