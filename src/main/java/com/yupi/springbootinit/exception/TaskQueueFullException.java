package com.yupi.springbootinit.exception;

public class TaskQueueFullException extends RuntimeException {
    public TaskQueueFullException(String message) {
        super(message);
    }
}