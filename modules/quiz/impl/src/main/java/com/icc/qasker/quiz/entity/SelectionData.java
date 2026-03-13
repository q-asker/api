package com.icc.qasker.quiz.entity;

/** 비정규화된 선택지 데이터 — selection 테이블을 대체하는 값 객체. */
public record SelectionData(String content, boolean correct) {}
