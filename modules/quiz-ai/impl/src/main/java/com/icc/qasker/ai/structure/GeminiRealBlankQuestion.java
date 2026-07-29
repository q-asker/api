package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * REAL_BLANK(직접 입력 단답) 생성 파싱 구조. 선택형과 달리 오답 선택지가 없고, 정답과 그 인정 범위(acceptedAnswers)를 산출한다.
 * BLANK/MULTIPLE/OX의 {@link GeminiQuestion}과 분리해 타 유형 스키마 오염을 막는다(FR-007).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRealBlankQuestion(
    @JsonPropertyDescription("질문문 (빈칸은 _______ 로 표기)") String content,
    @JsonPropertyDescription("이 문항에 적용된 Bloom's 수준") String bloomsLevel,
    @JsonPropertyDescription("참조한 강의노트 페이지 번호") List<Integer> referencedPages,
    @JsonPropertyDescription("모범답안. 다중 빈칸이면 빈칸 등장 순서대로 콤마(,)와 공백으로 구분") String answer,
    @JsonPropertyDescription(
            "빈칸별 정답 인정 표현 목록. 바깥 배열=빈칸 순서, 안쪽 배열=그 빈칸에서 정답으로 인정할 표현들"
                + "(모범답안 자신 + 동의어·이표기·약어·영↔한 표기). 표기 차이와 동의어까지만 포함하고 오탈자·인접/상하위 개념은 절대 포함 금지")
        List<List<String>> acceptedAnswers,
    @JsonPropertyDescription("해설 (정답 추론·근거)") String explanation,
    @JsonPropertyDescription("사용자 지시 반영 결과") String appliedInstruction) {}
