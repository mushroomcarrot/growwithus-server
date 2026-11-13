package community.likelion.growwithusserver.game.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 주간 퀘스트의 조건. 매주 종류마다 퀘스트가 하나씩 생긴다.
@Getter
@RequiredArgsConstructor
public enum QuestType {
    CHEER("응원 5번 하기", 5, 50),
    HEART("감사 하트 3번 보내기", 3, 30),
    EVENT("달라진 모습 확인하기", 1, 20);

    private final String title;
    // 달성에 필요한 횟수
    private final int goal;
    private final int rewardXp;
}
