package com.icc.qasker.quizhistory.dto.ferequest;

/** 기록을 폴더에 배정/해제. folderId가 null이면 미분류로 해제한다. */
public record AssignFolderRequest(String folderId) {}
