package com.eagle.user.service;

import com.eagle.user.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class GlobalUserGrpcService extends GlobalUserServiceGrpc.GlobalUserServiceImplBase {

    private final GlobalUserService globalUserService;

    public GlobalUserGrpcService(GlobalUserService globalUserService){
        this.globalUserService =  globalUserService;
    }

    @Override
    public void getAllGlobalUsers(Empty request, StreamObserver<UserList> responseObserver) {
        super.getAllGlobalUsers(request, responseObserver);
    }

    @Override
    public void getGlobalUser(UserIdRequest request, StreamObserver<UserList> responseObserver) {
        super.getGlobalUser(request, responseObserver);
    }

    @Override
    public void addGlobalUser(CreateUserRequest request, StreamObserver<UserList> responseObserver) {
        super.addGlobalUser(request, responseObserver);
    }

    @Override
    public void updateGlobalUser(UpdateUserRequest request, StreamObserver<UserList> responseObserver) {
        super.updateGlobalUser(request, responseObserver);
    }

    @Override
    public void deleteGlobalUser(UserIdRequest request, StreamObserver<Empty> responseObserver) {
        super.deleteGlobalUser(request, responseObserver);
    }

    @Override
    public void getGlobalUserToken(UserToken request, StreamObserver<UserList> responseObserver) {
        super.getGlobalUserToken(request, responseObserver);
    }

    @Override
    public void getUserNameForToken(TokenUsernameRequest request, StreamObserver<TokenUsernameResponse> responseObserver) {
        super.getUserNameForToken(request, responseObserver);
    }

    @Override
    public void updateUserPassword(UpdatePasswordRequest request, StreamObserver<Empty> responseObserver) {
        super.updateUserPassword(request, responseObserver);
    }
}
