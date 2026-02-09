package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReferencedPage extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int pageNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
            @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private Problem problem;

    // 이하 헬퍼 함수
    public static ReferencedPage of(int pageNumber, Problem problem) {
        return ReferencedPage.builder()
                .pageNumber(pageNumber)
                .problem(problem)
                .build();
    }
}
