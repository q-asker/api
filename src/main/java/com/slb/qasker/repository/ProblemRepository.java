package com.slb.qasker.repository;

import com.slb.qasker.entity.Problem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProblemRepository {
    // 임시 mock data
    private static final Map<Long, Problem> MOCK_DB = new HashMap<>();

    static {
        MOCK_DB.put(101L, new Problem(101L, "C", "C이기 때문"));
        MOCK_DB.put(102L, new Problem(102L, "B", "B이기 때문"));
        MOCK_DB.put(103L, new Problem(103L, "A", "A이기 때문"));
    }

    public Optional<Problem> findById(Long id) {
        return Optional.ofNullable(MOCK_DB.get(id));
    }
}
