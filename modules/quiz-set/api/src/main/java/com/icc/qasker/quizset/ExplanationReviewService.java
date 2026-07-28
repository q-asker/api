package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.ExplanationReviewResult;
import java.util.List;

/**
 * 해설 형식 검증(정규식) 온디맨드 서비스. 품질 로그의 해설(개선본 v2 우선, 없으면 첫 생성본 v1)을 정형 규칙으로 검증하고, 형식 위반 문항의 review 컬럼에만
 * 위반 요약을 마킹한다(서빙 problem 무접촉).
 */
public interface ExplanationReviewService {

  /** 여러 세트의 해설 형식을 한 트랜잭션에서 검증하고 위반 문항의 review를 마킹한다. 결과는 요청한 세트 순서로 돌려준다. */
  List<ExplanationReviewResult> review(List<Long> problemSetIds);
}
