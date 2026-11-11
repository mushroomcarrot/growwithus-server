package community.likelion.growwithusserver.weather.data.enums;

// 앱에 내려주는 날씨 상태 (명세의 weather.condition). 기상청 값에서 바꾸는 규칙은 WeatherConditionResolver에 있다.
public enum WeatherCondition {
    SUNNY,
    CLOUDY,
    RAIN,
    HOT,
    COLD
}
