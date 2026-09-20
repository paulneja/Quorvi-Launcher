package com.paulneja.quorvi.runtime;

interface IShellService {
    int version();
    String status();
    void install();
    String exec(String requestJson);
    boolean kill(String execId);
    int killAll();
    String export(String path, String displayName, String mime);
    String readText(String path, int maxBytes);
    String writeText(String path, String content);
    String list(String path);
}
