package com.localrag.storage.exception;

import com.localrag.common.exception.BaseException;

public class StorageException extends BaseException {
    public StorageException(int code, String message) {
        super(code, message);
    }

    public StorageException(String message) {
        this(500, message);
    }
}
