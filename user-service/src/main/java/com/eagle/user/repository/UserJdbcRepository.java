package com.eagle.user.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eagle.user.CreateUserRequest;
import com.eagle.user.GlobalUser;
import com.eagle.user.GlobalUserRole;
import com.eagle.user.TokenUsernameResponse;
import com.eagle.user.UpdateUserRequest;
import com.eagle.user.UserList;
import com.google.protobuf.ByteString;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Repository
public class UserJdbcRepository {

    private final DatabaseClient databaseClient;
    private final IdSequenceRepository idSequenceRepository;

    public UserJdbcRepository(DatabaseClient databaseClient, IdSequenceRepository idSequenceRepository) {
        this.databaseClient = databaseClient;
        this.idSequenceRepository = idSequenceRepository;
    }

    private Mono<String> generateUserId() {
        return idSequenceRepository.next("EGL")
                .map(seq -> "EGL" + String.format("%05d", seq));
    }

    // ------------------- INSERT USER -------------------
    private Mono<String> insertUser(CreateUserRequest user) {
        return generateUserId()
                .flatMap(userId -> {
                    // Build user with generated ID
                    CreateUserRequest userWithId = user.toBuilder().setId(userId).build();

                    return databaseClient.sql("""
                    INSERT INTO feedbackapp.global_user(
                        id, username, fullname, dob, email, password, mobile, is_active,
                        account_non_expired, account_non_locked, credential_non_expired,
                        profile_pic, created_date
                    ) VALUES(:id,:username, :fullname,:dob,:email,:password,:mobile,:active,
                           :expired,:locked,:credentialexpired,:pic,:createdDate)
                """)
                            .bind("id", userWithId.getId())
                            .bind("username", userWithId.getUsername())
                            .bind("fullname", userWithId.getFullname())
                            .bind("dob", userWithId.getDob())
                            .bind("email", userWithId.getEmail())
                            .bind("password", userWithId.getPassword())
                            .bind("mobile", userWithId.getMobile())
                            .bind("active", userWithId.getActive())
                            .bind("expired", userWithId.getAccountNonExpired())
                            .bind("locked", userWithId.getAccountNonLocked())
                            .bind("credentialexpired", userWithId.getCredentialsNonExpired())
                            .bind("pic", userWithId.getProfilePicture().toByteArray())
                            .bind("createdDate", Timestamp.valueOf(LocalDateTime.now()))
                            .then()
                            .thenReturn(userId); // return generated ID
                });
    }

    // ------------------- INSERT ROLE -------------------
    public Mono<Void> insertRolesBatch(String userId, List<GlobalUserRole> roles) {

        if (roles == null || roles.isEmpty() ) return Mono.empty();

        StringBuilder sql = new StringBuilder("""
            INSERT INTO feedbackapp.global_user_role(user_id, rolename) VALUES
        """);

        DatabaseClient.GenericExecuteSpec spec;

        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:user").append(i)
                    .append(", :role").append(i).append(")");
        }

        spec = databaseClient.sql(sql.toString());

        for (int i = 0; i < roles.size(); i++) {
            spec = spec
                    .bind("user"+i, userId)
                    .bind("role"+i, roles.get(i).getRolename());
        }

        return spec.then();
    }

    // ------------------- INSERT ADDRESSES -------------------
    private Mono<Void> insertAddresses(String userId, Map<String, String> addressMap) {
        return Flux.fromIterable(addressMap.entrySet())
                .flatMap(entry ->
                        databaseClient.sql("""
                    INSERT INTO feedbackapp.global_user_address(user_id, addr_key, addr_value)
                    VALUES (:userId, :key, :value)
                """)
                                .bind("userId", userId)
                                .bind("key", entry.getKey())
                                .bind("value", entry.getValue())
                                .then()
                )
                .then();
    }

    // ------------------- FULL TRANSACTION -------------------
    @Transactional
    public Mono<Void> createUserWithRolesAndAddresses(CreateUserRequest user) {
        return insertUser(user)
                .flatMap(userId ->
                        insertRolesBatch(userId, user.getGlobaluserroleList())
                                .then(insertAddresses(userId, user.getAddressMap()))
                );
    }


    // ------------------- GET ALL USERS -------------------
    public Mono<UserList> getAllUsers() {

        String sql = """
            SELECT u.id, u.fullname, u.dob, u.email, u.mobile,
                   u.is_active, u.account_non_expired, u.account_non_locked, u.credential_non_expired,
                   u.profile_pic,
                   r.id AS role_id, r.rolename,
                   a.addr_key, a.addr_value
            FROM feedbackapp.global_user u
            LEFT JOIN feedbackapp.global_user_role r ON r.user_id = u.id
            LEFT JOIN feedbackapp.global_user_address a ON a.user_id = u.id
            ORDER BY u.fullname
        """;

        return databaseClient.sql(sql)
                .map((row, meta) -> RowView.of(row))
                .all()
                .collect(
                        () -> new LinkedHashMap<String, GlobalUser.Builder>(),
                        (map, r) -> {

                            String id = r.getString("id");

                            GlobalUser.Builder user = map.computeIfAbsent(id, k ->
                                    GlobalUser.newBuilder()
                                            .setId(id)
                                            .setFullname(r.getString("fullname"))
                                            .setDob(r.getString("dob"))
                                            .setEmail(r.getString("email"))
                                            .setMobile(r.getLong("mobile"))
                                            .setActive(r.getBoolean("is_active"))
                                            .setAccountNonExpired(r.getBoolean("account_non_expired"))
                                            .setAccountNonLocked(r.getBoolean("account_non_locked"))
                                            .setCredentialsNonExpired(r.getBoolean("credential_non_expired"))
                            );

                            byte[] pic = r.getBytes("profile_pic");
                            if (pic != null) {
                                user.setProfilePicture(ByteString.copyFrom(pic));
                            }

                            Long roleId = r.getLong("role_id");
                            if (roleId != null) {
                                user.addGlobaluserrole(
                                        GlobalUserRole.newBuilder()
                                                .setId(roleId)
                                                .setRolename(r.getString("rolename"))
                                                .build()
                                );
                            }

                            String key = r.getString("addr_key");
                            if (key != null) {
                                user.putAddressMap(key, r.getString("addr_value"));
                            }
                        }
                )
                .map(map -> UserList.newBuilder()
                        .addAllGlobaluser(
                                map.values().stream().map(GlobalUser.Builder::build).toList()
                        )
                        .build()
                );
    }


    // ------------------- GET USERS BY ID -------------------
    public Mono<UserList> getGlobalUserById(String id) {

        String sql = """
            SELECT u.id, u.fullname, u.dob, u.email, u.mobile, u.is_active,
                   u.account_non_expired, u.account_non_locked, u.credential_non_expired,
                   u.profile_pic,
                   r.id AS role_id, r.rolename,
                   a.addr_key, a.addr_value
            FROM feedbackapp.global_user u
            LEFT JOIN feedbackapp.global_user_role r ON r.user_id = u.id
            LEFT JOIN feedbackapp.global_user_address a ON a.user_id = u.id
            WHERE u.id = :id
        """;

        return databaseClient.sql(sql)
                .bind("id", id)
                .map((row, meta) -> RowView.of(row))
                .all()
                .collect(
                        () -> new LinkedHashMap<String, GlobalUser.Builder>(),
                        (map, r) -> {
                            GlobalUser.Builder user = map.computeIfAbsent(id, k ->
                                    GlobalUser.newBuilder()
                                            .setId(id)
                                            .setFullname(r.getString("fullname"))
                                            .setDob(r.getString("dob"))
                                            .setEmail(r.getString("email"))
                                            .setMobile(r.getLong("mobile"))
                                            .setActive(r.getBoolean("is_active"))
                                            .setAccountNonExpired(r.getBoolean("account_non_expired"))
                                            .setAccountNonLocked(r.getBoolean("account_non_locked"))
                                            .setCredentialsNonExpired(r.getBoolean("credential_non_expired"))
                            );

                            byte[] pic = r.getBytes("profile_pic");
                            if (pic != null) {
                                user.setProfilePicture(ByteString.copyFrom(pic));
                            }

                            Long roleId = r.getLong("role_id");
                            if (roleId != null) {
                                user.addGlobaluserrole(
                                        GlobalUserRole.newBuilder()
                                                .setId(roleId)
                                                .setRolename(r.getString("rolename"))
                                                .build()
                                );
                            }

                            String key = r.getString("addr_key");
                            if (key != null) {
                                user.putAddressMap(key, r.getString("addr_value"));
                            }
                        }
                )
                .map(map -> UserList.newBuilder()
                        .addAllGlobaluser(
                                map.values().stream().map(GlobalUser.Builder::build).toList()
                        )
                        .build()
                );
    }



    //------------------- UPDATE USERS -------------------
    public Mono<Void> updateUser(UpdateUserRequest user){
        return databaseClient.sql(""" 
    			UPDATE feedbackapp.global_user SET 
    			fullname = :fullname, dob = :dob, is_active = :active,
        account_non_expired = :expired, account_non_locked = :locked, credential_non_expired = :credentialexpired,
        profile_pic = :pic WHERE id = :id
    			""")
                .bind("id", user.getId())
                .bind("fullname", user.getFullname())
                .bind("dob", user.getDob())
//                .bind("email", user.getEmail())
//                .bind("mobile", user.getMobile())
                .bind("active", user.getActive())
                .bind("expired", user.getAccountNonExpired())
                .bind("locked", user.getAccountNonLocked())
                .bind("credentialexpired", user.getCredentialsNonExpired())
                .bind("pic", user.getProfilePicture().toByteArray())
                .then();
    }


    //------------------- UPDATE USERS ROLE -------------------
    public Mono<Void> deleteRolesByUser(String userId){
        return databaseClient.sql(""" 
    			DELETE FROM feedbackapp.global_user_role WHERE user_id = :id
    			""")
                .bind("id", userId).then();
    }

    public Mono<Void> deleteAddressesByUser(String userId) {
        return databaseClient.sql("""
            DELETE FROM feedbackapp.global_user_address WHERE user_id = :id
        """).bind("id", userId).then();
    }

    public Mono<Void> replaceAddresses(UpdateUserRequest user) {

        if (user.getAddressMapMap().isEmpty()) return Mono.empty();

        StringBuilder sql = new StringBuilder("""
            INSERT INTO feedbackapp.global_user_address(user_id, addr_key, addr_value) VALUES
        """);

        int i = 0;
        for (var e : user.getAddressMapMap().entrySet()) {
            if (i > 0) sql.append(",");
            sql.append("(:user").append(i)
                    .append(", :key").append(i)
                    .append(", :val").append(i).append(")");
            i++;
        }

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());

        i = 0;
        for (var e : user.getAddressMapMap().entrySet()) {
            spec = spec
                    .bind("user"+i, user.getId())
                    .bind("key"+i, e.getKey())
                    .bind("val"+i, e.getValue());
            i++;
        }

        return spec.then();
    }


    @Transactional
    public Mono<Void> udateUserWithRolesAndAddress(UpdateUserRequest user){
        return deleteRolesByUser(user.getId())
                .then(insertRolesBatch(user.getId(), user.getGlobaluserroleList())
                        .then(deleteAddressesByUser(user.getId())
                                .then(replaceAddresses(user))));
    }


    @Transactional
    public Mono<Void> deleteUser(String userId) {
        return databaseClient.sql("""
            DELETE FROM feedbackapp.global_user WHERE id = :id
        """).bind("id", userId).then();
    }

    @Transactional
    public Mono<Void> updatePassword(String username, String password){
        String sql = """
    	        UPDATE feedbackapp.global_user
    	        SET password = :password
    	        WHERE username = :username
    	        """;

        return databaseClient.sql(sql)
                .bind("password", password)
                .bind("username", username)
                .then();
    }


    public Mono<Boolean> existsByEmailOrContact(String email, long mobile) {
        return databaseClient.sql("""
                 SELECT COUNT(*) FROM feedbackapp.global_user 
                 WHERE email = :email OR mobile = :mobile
             """)
                .bind("email",email)
                .bind("mobile", mobile)
                .map((row, meta) -> row.get("count", Long.class) > 0)
                .one()
                .defaultIfEmpty(false);
    }

    public record RowView(Row row) {
        static RowView of(Row r) { return new RowView(r); }

        String getString(String c) { return row.get(c, String.class); }
        Long getLong(String c) { return row.get(c, Long.class); }
        Boolean getBoolean(String c) { return row.get(c, Boolean.class); }
        byte[] getBytes(String c) { return row.get(c, byte[].class); }
    }


    public Mono<TokenUsernameResponse> getUserByUsername(String tokenUsername) {

        String sql = """
             SELECT u.id, u.username, u.password, r.id AS role_id, r.rolename,
             u.is_active, u.account_non_expired, u.account_non_locked, u.credential_non_expired
             FROM feedbackapp.global_user u
             LEFT JOIN feedbackapp.global_user_role r ON r.user_id = u.id
             WHERE u.username = :username
         """;

        return databaseClient.sql(sql)
                .bind("username", tokenUsername)
                .map((row, meta) -> RowView.of(row))
                .all()
                .collect(
                        () -> new LinkedHashMap<String, TokenUsernameResponse.Builder>(),
                        (map, r) -> {

                            String userId = r.getString("id");
                            TokenUsernameResponse.Builder user = map.computeIfAbsent(userId, k ->
                                    TokenUsernameResponse.newBuilder()
                                            .setId(userId)
                                            .setUsername(r.getString("username"))
                                            .setPassword(r.getString("password"))
                                            .setActive(r.getBoolean("is_active"))
                                            .setAccountNonExpired(r.getBoolean("account_non_expired"))
                                            .setAccountNonLocked(r.getBoolean("account_non_locked"))
                                            .setCredentialsNonExpired(r.getBoolean("credential_non_expired"))
                            );

                            Long roleId = r.getLong("role_id");
                            if (roleId != null) {
                                user.addGlobaluserrole(
                                        GlobalUserRole.newBuilder()
                                                .setId(roleId)
                                                .setRolename(r.getString("rolename"))
                                                .build()
                                );
                            }
                        }
                )
                .flatMap(map ->
                        map.values().stream()
                                .findFirst()
                                .map(builder -> Mono.just(builder.build()))
                                .orElse(Mono.empty())
                );
    }
}
