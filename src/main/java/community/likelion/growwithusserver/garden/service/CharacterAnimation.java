package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;

// 캐릭터의 기분과 현재 날씨로 애니메이션과 한마디를 정한다 (명세의 character.animation).
public final class CharacterAnimation {
    public static final String SUNNY_HAPPY = "sunny_happy";
    public static final String RAIN_UMBRELLA = "rain_umbrella";
    public static final String HOT_SWEAT = "hot_sweat";
    public static final String SAD_DROOP = "sad_droop";

    private CharacterAnimation() {
    }

    /**
     * 시무룩한 상태가 날씨보다 우선한다. 그다음은 비, 더위 순이고 나머지 날씨는 모두 sunny_happy다.
     *
     * @param condition 현재 날씨. 수집된 값이 없으면 null
     */
    public static String resolve(CharacterMood mood, WeatherCondition condition) {
        if (mood == CharacterMood.SAD) {
            return SAD_DROOP;
        }
        if (condition == WeatherCondition.RAIN) {
            return RAIN_UMBRELLA;
        }
        if (condition == WeatherCondition.HOT) {
            return HOT_SWEAT;
        }
        return SUNNY_HAPPY;
    }

    public static String message(CharacterMood mood, WeatherCondition condition) {
        if (mood == CharacterMood.SAD) {
            return "응원을 못 받아서 시무룩해요";
        }
        if (condition == null) {
            return "오늘도 잘 자라고 있어요";
        }
        return switch (condition) {
            case SUNNY -> "오늘 햇살이 좋아서 기분이 좋아요";
            case CLOUDY -> "구름이 많지만 잘 자라고 있어요";
            case RAIN -> "비가 와서 우산을 썼어요";
            case HOT -> "날이 더워서 땀이 나요";
            case COLD -> "날이 추워서 몸을 웅크리고 있어요";
        };
    }
}
