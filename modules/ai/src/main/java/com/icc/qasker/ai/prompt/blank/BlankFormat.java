package com.icc.qasker.ai.prompt.blank;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlankFormat {

    public static final String content = """
            ### title(빈칸 포함 전제+질문) 출력 구조
            #### 출력 구조(형태만 준수)
            {빈칸 포함 전제(문장 안에 "_______" 1개 포함)}
        
            ---
        
            ### selections(content 값) 출력 구조
            #### 포함 정보(구조만 준수)
            - selections: 선택지 4개로 구성
            - 각 선택지 필드: content(string), correct(boolean)
            - correct 분포: true 1개 + false 3개
        
            ---
        
            ### explanation(해설) 출력 구조
            #### 출력 구조
            [정답] 선택지 내용: {정답 선택지 내용}
            (개행)
            이유: {정답 근거 1문장(정의/역할/문맥 적합성)}
            (개행)
            [오답 1] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답이 되는 이유 1문장(문맥 불일치/범주 불일치/혼동 포인트)}
            (개행)
            [오답 2] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답이 되는 이유 1문장(문맥 불일치/범주 불일치/혼동 포인트)}
            (개행)
            [오답 3] 선택지 내용: {오답 선택지 내용}
            (개행)
            이유: {오답이 되는 이유 1문장(문맥 불일치/범주 불일치/혼동 포인트)}
            (개행)
        """;
}
