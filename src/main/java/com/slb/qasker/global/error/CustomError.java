package com.slb.qasker.global.error;

public class CustomError extends RuntimeException {

    public CustomError(
        ErrorMessage errorMessage
    ) {
        super(errorMessage.getMessage());
    }
}
