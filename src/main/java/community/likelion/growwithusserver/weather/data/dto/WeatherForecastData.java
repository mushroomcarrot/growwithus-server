package community.likelion.growwithusserver.weather.data.dto;

import java.time.LocalDateTime;

/**
 * 수집한 날씨 한 건. 기상청 클라이언트가 응답을 이 형태로 바꿔 WeatherService.saveForecasts에 넘긴다.
 *
 * @param forecastAt        예보 또는 관측 대상 시각
 * @param temperature       기온 (℃)
 * @param rainProbability   강수확률 (%), 실황이면 null
 * @param precipitationType 강수형태 코드 (PTY)
 * @param skyStatus         하늘상태 코드 (SKY), 실황이면 null
 * @param observed          초단기실황이면 true
 */
public record WeatherForecastData(LocalDateTime forecastAt, Double temperature, Integer rainProbability,
                                  Integer precipitationType, Integer skyStatus, boolean observed) {
}
