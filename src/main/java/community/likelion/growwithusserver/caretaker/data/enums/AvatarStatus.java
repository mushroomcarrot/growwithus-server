package community.likelion.growwithusserver.caretaker.data.enums;

// AI 아바타 생성 작업 상태. 클라이언트는 PENDING인 동안 GET /caretakers/me/avatar를 폴링한다.
public enum AvatarStatus {
    PENDING,
    DONE,
    FAILED
}
