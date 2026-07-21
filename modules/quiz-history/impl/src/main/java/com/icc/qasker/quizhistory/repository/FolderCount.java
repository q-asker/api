package com.icc.qasker.quizhistory.repository;

/** 폴더별 기록 수 집계 프로젝션. */
public interface FolderCount {
  Long getFolderId();

  long getCount();
}
