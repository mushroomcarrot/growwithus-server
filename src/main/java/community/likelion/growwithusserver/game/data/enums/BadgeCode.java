package community.likelion.growwithusserver.game.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 배지 목록. 새 배지는 여기에 추가하면 부팅할 때 badge 테이블에 들어간다.
@Getter
@RequiredArgsConstructor
public enum BadgeCode {
    FIRST_SPROUT("첫 새싹", "작물이 처음 싹을 틔웠어요"),
    FIRST_FLOWER("첫 꽃", "작물이 처음 꽃을 피웠어요"),
    FIRST_FRUIT("첫 열매", "작물이 처음 열매를 맺었어요"),
    STREAK_7("일주일 응원", "7일 연속으로 응원했어요"),
    LEAGUE_FIRST("이웃 텃밭 1등", "주간 리그에서 1등을 했어요");

    private final String displayName;
    private final String description;
}
