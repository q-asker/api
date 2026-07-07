package com.icc.qasker.quizset;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** quiz-set-impl 모듈 단독 JPA 슬라이스 테스트용 부트 설정. 운영 진입점은 app 모듈이다. */
@SpringBootApplication
@EnableJpaAuditing
public class QuizSetJpaTestApplication {}
