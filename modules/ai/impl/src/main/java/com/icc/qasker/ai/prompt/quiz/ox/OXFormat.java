package com.icc.qasker.ai.prompt.quiz.ox;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OXFormat {

    public static final String content = """
        ### title(단일 진술문) 출력 구조
        #### 출력 구조(형태만 준수)
        {단일 진술문 1문장}
        
        ---
        
        ### selections(content 값) 출력 구조
        #### 포함 정보(구조만 준수)
        - correct 분포: true 1개 + false 1개
        - content 표기: "O", "X" 두 값으로만 구성
        - correct=true는 "O" 또는 "X" 중 하나가 될 수 있음(고정 금지)
        
        ---
        
        ### explanation(해설) 출력 구조
        #### 출력 구조
        [정답] 선택지 내용: {O 또는 X}
        (개행)
        이유: {판정 근거 1~2문장(조건/예외/인과 중 핵심)}
        (개행)
        [오답] 선택지 내용: {X 또는 O}
        (개행)
        이유: {오답이 되는 이유 1문장(조건 누락/과잉 일반화/맥락 누락 중 하나)}
        (개행)
        """;
}
