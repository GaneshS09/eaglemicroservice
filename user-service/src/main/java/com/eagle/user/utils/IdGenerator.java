package com.eagle.user.utils;

import java.util.Optional;
import java.util.function.Function;

public class IdGenerator<T> {

    private final Function<T, String> idGetterFunction;
    private final Function<String, Long> extratorFunction;
    private final Function<Long, String> formatterFunctiona;

    public IdGenerator(Function<T, String> idGetterFunction,
                       Function<String, Long> extratorFunction,
                       Function<Long, String> formatterFunctiona){
        this.idGetterFunction = idGetterFunction;
        this.extratorFunction = extratorFunction;
        this.formatterFunctiona = formatterFunctiona;
    }

    public String generateId(String prefix, Optional<T> lastEntry){
        long next = lastEntry
                .map(idGetterFunction)
                .map(extratorFunction)
                .map(n -> n + 1)
                .orElse(1L);

        return prefix + formatterFunctiona.apply(next);
    }
}
