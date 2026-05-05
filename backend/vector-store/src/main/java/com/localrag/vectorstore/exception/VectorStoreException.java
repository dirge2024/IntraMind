package com.localrag.vectorstore.exception;

import com.localrag.common.exception.BaseException;

public class VectorStoreException extends BaseException {
    public VectorStoreException(String message) {
        super(500, message);
    }
}
