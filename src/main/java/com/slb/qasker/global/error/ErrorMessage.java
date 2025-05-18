package com.slb.qasker.global.error;

public enum ErrorMessage {

    NO_FILE_UPLOADED("파일이 업로드되지 않았습니다."),
    FILE_NAME_NOT_EXIST("파일 이름이 존재하지 않습니다"),
    FILE_NAME_TOO_LONG("파일 이름이 깁니다"),
    EXTENSION_NOT_EXIST("확장자가 존재하지 않습니다"),
    EXTENSION_INVALID("허용하지 않는 확장자입니다.");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
