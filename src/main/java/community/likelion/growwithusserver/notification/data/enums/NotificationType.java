package community.likelion.growwithusserver.notification.data.enums;

// 알림 종류. 명세(api-detail.md 7.1)의 type 값과 같다.
public enum NotificationType {
    // 관리사가 작업을 끝냄 (주인에게)
    CARE_DONE,
    // 주인이 감사 하트를 보냄 (관리사에게)
    HEART,
    // 작물 단계가 오름, 진화 이벤트 (주인에게)
    STAGE_UP,
    // 주말 방문 예약 (관리사에게)
    VISIT_RESERVED,
    // 방문 예약 취소 (관리사에게)
    VISIT_CANCELED,
    // 오늘 응원을 안 해서 스트릭이 끊길 위험 (주인에게)
    STREAK_RISK,
    // 주간 퀘스트 달성 (주인에게)
    QUEST_DONE
}
