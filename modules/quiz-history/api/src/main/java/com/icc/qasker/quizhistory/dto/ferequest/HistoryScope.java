package com.icc.qasker.quizhistory.dto.ferequest;

/** 히스토리 목록 탐색 범위. ALL=전체, UNCLASSIFIED=미분류, FOLDER=특정 폴더(folderId 필요). */
public enum HistoryScope {
  ALL,
  UNCLASSIFIED,
  FOLDER
}
