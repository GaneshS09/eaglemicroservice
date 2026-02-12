package com.eagle.user.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;

@GrpcAdvice
public class GlobalExceptionHandler {

    public StatusRuntimeException handleDuplicate(DuplicateUserException ex) {
        return Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException();
    }
}
