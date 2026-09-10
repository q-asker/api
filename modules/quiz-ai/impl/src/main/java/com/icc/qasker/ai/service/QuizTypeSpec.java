package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.global.quiz.QuizType;
import java.util.List;
import java.util.Optional;

public interface QuizTypeSpec<T> {

  QuizType getSupportedType();

  /** 이 유형의 생성 지침(시스템 프롬프트). 출력 언어 지시가 덧붙는다. */
  String systemGuideLine(String language);

  /** 청크 하나를 요청하는 유저 프롬프트. 유형에 따라 난이도·정답 배분 등이 달라진다. */
  String requestPrompt(int quizCount, String customInstruction);

  /** 스트리밍 응답에서 배열 원소를 역직렬화할 타입. */
  Class<T> elementType();

  /** 두 번째 청크부터 요청 프롬프트 끝에 붙는 중복 방지 지시. */
  String dedupInstruction();

  /** 응답 JSON 스키마. 사용자 지시가 있으면 스키마에 반영한다. */
  String responseSchema(String customInstruction);

  /** 채택 여부. 유형 규칙에 어긋나는 문항(예: 선지 수 초과)을 여기서 걸러낸다. */
  boolean accept(T question);

  /** 파싱 구조를 저장 형태로 옮긴다. 선지 정렬처럼 저장 순서를 확정하는 일도 여기서 끝낸다. */
  AIProblem toProblem(T question, List<Integer> sourcePages);

  /** 재생성 응답(문항 1개)에서 첫 원소를 꺼낸다. 비어 있으면 재생성 실패로 본다. */
  Optional<T> parseFirst(String text);
}
