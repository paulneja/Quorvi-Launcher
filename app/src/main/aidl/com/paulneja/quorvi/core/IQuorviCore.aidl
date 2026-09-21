package com.paulneja.quorvi.core;

interface IQuorviCore {
    int version();
    String submitRun(String specJson);
    void cancelRun(String runId);
    void pauseRun(String runId);
    void resumeRun(String runId);
    void refreshConnections(boolean force);
    void setVisible(boolean visible);
    void syncExecution(String executionId);
    void answerRun(String runId, String answer);
    void killAll();
    String runCapsule(String capsuleId);
}
