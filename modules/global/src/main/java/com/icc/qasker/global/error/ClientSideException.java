package com.icc.qasker.global.error;

public class ClientSideException extends CustomException {

    public ClientSideException(ExceptionMessage exceptionMessage) {
        super(exceptionMessage);
    }
}
