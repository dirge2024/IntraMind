/** @RestControllerAdvice 全局异常拦截，将 BaseException 和未捕获异常统一转为 Result 返回。 */
package com.localrag.common.exception;

import com.localrag.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail(500, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
