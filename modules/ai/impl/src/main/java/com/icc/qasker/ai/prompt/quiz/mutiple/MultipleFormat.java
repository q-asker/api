package com.icc.qasker.ai.prompt.quiz.mutiple;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MultipleFormat {

    public static final String content = """
            ### 출력 구조(스키마 기반)
            - 문제 유형: 단일정답형 객관식(Multiple Choice)
        
            ### title(전제+질문) 출력 구조
            #### 출력 구조(강제)
            [전제] {전제, 각 문장마다 개행}
            (개행)
            [질문] {질문}
            ex) 아래와 같이 출력
            [전제] foo\\nbar\\nbaz
            (개행)
            [질문] qux
        
            #### title 형식 강제 규칙(반드시 준수)
            - title은 **오직 2개 블록**만 포함한다: **[전제]** 블록 + **[질문]** 블록
            - title의 **첫 4글자**는 반드시 "[전제]" 이어야 한다(대괄호 포함, 철자 동일)
            - "[질문]" 라벨은 **정확히 1번만** 등장해야 하며, **항상 마지막 블록**이어야 한다
            - "[전제]" 블록 내부에는 **의문문(?, 질문형 문장)**을 넣지 않는다
            - 두 블록 사이에는 **빈 줄 1개(개행 2번)**만 둔다(그 외 공백 줄 추가 금지)
            - "[전제]", "[질문]" 외의 라벨(예: [문제], [설명])을 title에 쓰지 않는다
            - 출력 전 self-check: 위 규칙 중 하나라도 위반이면 **스스로 수정해서** 규칙을 만족한 title을 출력한다
        
            ---
        
            ### selections(선택지 4개) 출력 구조
            #### 포함 정보
            - 선택지는 4개
            - 정답 1개 + 오답 3개
            ---\s
        
            ### explanation(해설) 규칙(강제)
            #### 출력 구조
            [정답] 선택지 내용: {정답 선택지 내용}
            (개행)
            이유: {정답 근거 설명}
            (개행)
            [오답 1] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답 오류 설명}
            (개행)
            [오답 2] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답 오류 설명}
            (개행)
            [오답 3] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답 오류 설명}
            (개행)
        """;
}
