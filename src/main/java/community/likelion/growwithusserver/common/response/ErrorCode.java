package community.likelion.growwithusserver.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_REQUEST("C001", "잘못된 요청입니다.", 400),
    INVALID_PARAMETER("C002", "유효하지 않은 파라미터입니다.", 400),
    FILE_NOT_SUPPORTED("C4001", "지원하지 않는 파일입니다.", 400),
    CANCEL_DEADLINE_PASSED("C4005", "취소 기한이 지났습니다.", 400),
    VERIFICATION_INVALID("C4011", "인증 정보가 유효하지 않습니다.", 401),
    PASSWORD_INVALID("C4012", "비밀번호가 일치하지 않습니다.", 401),
    TOKEN_INVALID("C4013", "유효하지 않은 토큰입니다.", 401),
    SERVICE_KEY_INVALID("C4014", "서비스 키가 유효하지 않습니다.", 401),
    ACCESS_DENIED("C403", "승인되지 않은 사용자입니다.", 403),
    ROLE_NOT_SELECTED("C4031", "역할을 먼저 선택해야 합니다.", 403),
    DEMO_MODE_REQUIRED("C4032", "시연 모드에서만 사용할 수 있습니다.", 403),
    USER_DATA_NOT_FOUND("C4041", "사용자를 찾을 수 없습니다.", 404),
    DATA_NOT_FOUND("C404", "정보를 불러올 수 없습니다.", 404),
    USERNAME_DUPLICATION("C4092", "이미 사용 중인 아이디입니다.", 409),
    PLOT_ALREADY_ASSIGNED("C4093", "이미 분양된 칸입니다.", 409),
    PROFILE_ALREADY_EXISTS("C4094", "이미 프로필이 등록되어 있어요.", 409),
    ALREADY_PROCESSED("C4095", "이미 처리된 요청입니다.", 409),
    VISIT_SLOT_FULL("C4096", "예약이 마감된 시간입니다.", 409),
    UNKNOWN_ERROR("C500", "오류가 발생하였습니다.", 500),
    FILE_UPLOAD_FAILED("C5001", "파일 저장에 실패했습니다.", 500),
    EXTERNAL_API_ERROR("C502", "외부 서비스 호출에 실패했습니다.", 502);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}