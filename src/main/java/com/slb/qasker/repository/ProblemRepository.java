package com.slb.qasker.repository;

import com.slb.qasker.entity.Problem;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
@Repository
public class ProblemRepository {
    // 임시 mock data
    // DB 연결 후 수정 예정
    private static final Map<Long, Problem> MOCK_DB = new HashMap<>();

    static {
        MOCK_DB.put(1L, new Problem(1L, "C", "C이기 때문"));
        MOCK_DB.put(2L, new Problem(2L, "B", "B이기 때문"));
        MOCK_DB.put(3L, new Problem(3L, "A", "A이기 때문"));
    }

    public Optional<Problem> findById(Long id) {
        return Optional.ofNullable(MOCK_DB.get(id));
    }
}
