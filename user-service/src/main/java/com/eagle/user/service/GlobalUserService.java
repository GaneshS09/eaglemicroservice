package com.eagle.user.service;

import com.eagle.user.UserList;
import com.eagle.user.exception.DuplicateUserException;
import com.eagle.user.repository.UserJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eagle.user.CreateUserRequest;
import com.eagle.user.TokenUsernameRequest;
import com.eagle.user.TokenUsernameResponse;
import com.eagle.user.UpdatePasswordRequest;
import com.eagle.user.UpdateUserRequest;

import reactor.core.publisher.Mono;

@Service
@Transactional
public class GlobalUserService {

    private final UserJdbcRepository userJdbcRepository;

    public GlobalUserService(UserJdbcRepository userJdbcRepository) {
        this.userJdbcRepository = userJdbcRepository;
    }

    public Mono<UserList> create(CreateUserRequest user) {
        return userJdbcRepository.existsByEmailOrContact(user.getEmail(), user.getMobile())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateUserException("Email or mobile already registered"));
                    }
                    return userJdbcRepository.createUserWithRolesAndAddresses(user)
                            .then(
                                    userJdbcRepository.getGlobalUserById(user.getId()));
                });
    }

    public Mono<UserList> getAllGlobalUser() {
        return userJdbcRepository.getAllUsers();
    }

    public Mono<UserList> getGlobalUserById(String id) {
        return userJdbcRepository.getGlobalUserById(id);
    }


    @Transactional
    public Mono<UserList> updateGlobalUser(UpdateUserRequest user){
        return userJdbcRepository.udateUserWithRolesAndAddress(user)
                .then(
                        userJdbcRepository.getGlobalUserById(user.getId()));
    }

    @Transactional
    public Mono<Void> deleteGlobalUser(String id) {
        return userJdbcRepository.deleteAddressesByUser(id)
                .then(userJdbcRepository.deleteRolesByUser(id)
                        .then(userJdbcRepository.deleteUser(id)));
    }

    public Mono<TokenUsernameResponse> getUserDetail(TokenUsernameRequest tokenUsername) {
        return userJdbcRepository.getUserByUsername(tokenUsername.getUsername());
    }

    @Transactional
    public Mono<Void> UpdatePassword(UpdatePasswordRequest request){
        return userJdbcRepository.updatePassword(request.getUsername(),request.getNewPassword());
    }
}
