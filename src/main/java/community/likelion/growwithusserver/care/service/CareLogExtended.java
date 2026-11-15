package community.likelion.growwithusserver.care.service;

import java.util.List;

// 이미 있는 돌봄 기록에 작업이 더 묶였을 때 낸다. 가족 알림은 다시 보내지 않고 일기 사본만 맞춘다.
public record CareLogExtended(Long careLogId, List<String> taskTypes, String summary) {}
