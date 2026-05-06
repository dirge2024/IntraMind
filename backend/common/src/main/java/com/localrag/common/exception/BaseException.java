/** 全局业务异常基类，携带错误码供 GlobalExceptionHandler 统一处理。 */
package com.localrag.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {
    private final int code;

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(String message) {
        super(message);
        this.code = 500;
    }
}
