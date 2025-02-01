package com.yupi.springbootinit.model.enums;

public enum TaskStatusEnum {
    WAIT("wait"),
    RUNNING("running"),
    SUCCEED("succeed"),
    FAILED("failed");

    private final String status;

    TaskStatusEnum(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}