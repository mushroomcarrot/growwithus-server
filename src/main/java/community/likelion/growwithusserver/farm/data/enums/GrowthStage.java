package community.likelion.growwithusserver.farm.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 작물의 성장 단계 (명세의 stage). level은 1~4단계다.
@Getter
@RequiredArgsConstructor
public enum GrowthStage {
    SEED(1),
    SPROUT(2),
    FLOWER(3),
    FRUIT(4);

    private final int level;
}
