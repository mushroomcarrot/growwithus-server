package community.likelion.growwithusserver.weather.service;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;

/**
 * 기상청 값을 weather.condition으로 바꾸는 규칙. 위에서부터 먼저 맞는 것을 쓴다.
 *
 * 1. 눈 (PTY 3, 7): COLD
 * 2. 비, 소나기, 빗방울, 비/눈 (PTY 1, 2, 4, 5, 6): RAIN
 * 3. 기온 30℃ 이상: HOT
 * 4. 기온 5℃ 이하: COLD
 * 5. 구름많음, 흐림 (SKY 3, 4): CLOUDY
 * 6. 그 외 (맑음, 값 없음): SUNNY
 *
 * 강수확률은 쓰지 않는다. 지금 비가 오지 않는데 캐릭터가 우산을 쓰면 어색하기 때문이다.
 * "오후에 비가 와요" 같은 예보 판단은 작업 생성 쪽에서 강수확률을 직접 본다.
 */
public final class WeatherConditionResolver {
    public static final double HOT_TEMPERATURE = 30.0;
    public static final double COLD_TEMPERATURE = 5.0;

    private static final int PTY_NONE = 0;
    private static final int PTY_SNOW = 3;
    private static final int PTY_SNOW_FLURRY = 7;
    private static final int SKY_MOSTLY_CLOUDY = 3;
    private static final int SKY_CLOUDY = 4;

    private WeatherConditionResolver() {
    }

    public static WeatherCondition resolve(Double temperature, Integer precipitationType, Integer skyStatus) {
        if (precipitationType != null && precipitationType != PTY_NONE) {
            boolean snow = precipitationType == PTY_SNOW || precipitationType == PTY_SNOW_FLURRY;
            return snow ? WeatherCondition.COLD : WeatherCondition.RAIN;
        }

        if (temperature != null && temperature >= HOT_TEMPERATURE) {
            return WeatherCondition.HOT;
        }
        if (temperature != null && temperature <= COLD_TEMPERATURE) {
            return WeatherCondition.COLD;
        }

        if (skyStatus != null && (skyStatus == SKY_MOSTLY_CLOUDY || skyStatus == SKY_CLOUDY)) {
            return WeatherCondition.CLOUDY;
        }

        return WeatherCondition.SUNNY;
    }
}
