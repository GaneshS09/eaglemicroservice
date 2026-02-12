package com.eagle.user.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Repository
public class IdSequenceRepository {

    private final DatabaseClient dbClient;

    public IdSequenceRepository(DatabaseClient dbClient){
        this.dbClient = dbClient;
    }

    public Mono<Long> next(String prefix){

        return dbClient.sql("""
                INSERT INTO feedbackapp.id_sequence(prefix, last_value)
                VALUES (:prefix, 1)
                ON CONFLICT (prefix)
                DO UPDATE SET last_value = feedbackapp.id_sequence.last_value + 1
                RETURNING last_value
                """)
                .bind("prefix", prefix)
                .map((row, meta) -> row.get("last_value", Long.class))
                .one();
    }
}
