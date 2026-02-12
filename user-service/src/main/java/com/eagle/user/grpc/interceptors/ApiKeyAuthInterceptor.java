package com.eagle.user.grpc.interceptors;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import java.util.Objects;

@GrpcGlobalServerInterceptor
public class ApiKeyAuthInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {

        Metadata.Key<String> apiMetadataKey = Metadata.Key.of("eagle-api-key", Metadata.ASCII_STRING_MARSHALLER);
        String apiKey = metadata.get(apiMetadataKey);
        if(Objects.nonNull(apiKey) && apiKey.equals("eagle")){
            return serverCallHandler.startCall(serverCall, metadata);
        } else {
            Status status = Status.UNAUTHENTICATED.withDescription("Invalid API Key");
            serverCall.close(status, metadata);
        }
        return new ServerCall.Listener<>(){};
    }
}
