package community.likelion.growwithusserver.common.response;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<GlobalResponse<?>> handleGlobalException(GlobalException e) {
        HttpStatus status = HttpStatus.valueOf(e.getErrorCode().getHttpStatus());

        return ResponseEntity
                .status(status)
                .body(GlobalResponse.error(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_PARAMETER.getMessage());
        log.warn("[handleValidationException] 요청 값 검증 실패: {}", detail);

        return ResponseEntity
                .status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(GlobalResponse.error(ErrorCode.INVALID_PARAMETER));
    }

    // 본문 JSON을 읽을 수 없거나 (허용되지 않는 enum 값 포함), 필수 쿼리 파라미터가 빠졌거나, 쿼리와 경로 값의 형식이 틀린 경우 (날짜 등)
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<GlobalResponse<?>> handleUnreadableRequestException(Exception e) {
        log.warn("[handleUnreadableRequestException] 요청 형식 오류: {}", e.getMessage());

        return ResponseEntity
                .status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(GlobalResponse.error(ErrorCode.INVALID_PARAMETER));
    }

    // multipart 최대 크기(spring.servlet.multipart) 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<GlobalResponse<?>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("[handleMaxUploadSizeExceededException] 업로드 크기 제한 초과: {}", e.getMessage());

        return ResponseEntity
                .status(ErrorCode.FILE_NOT_SUPPORTED.getHttpStatus())
                .body(GlobalResponse.error(ErrorCode.FILE_NOT_SUPPORTED));
    }

    // 매핑된 컨트롤러가 없는 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GlobalResponse<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity
                .status(ErrorCode.DATA_NOT_FOUND.getHttpStatus())
                .body(GlobalResponse.error(ErrorCode.DATA_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<?>> handleException(Exception e) {
        log.error("[handleException] 처리되지 않은 예외 발생", e);

        return ResponseEntity
                .status(ErrorCode.UNKNOWN_ERROR.getHttpStatus())
                .body(GlobalResponse.error(ErrorCode.UNKNOWN_ERROR));
    }
}
