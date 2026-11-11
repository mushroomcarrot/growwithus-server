package community.likelion.growwithusserver.weather.client;

import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 응답 줄을 시각별 한 건(WeatherForecastData)으로 묶는다.
 *
 * 쓰는 요소는 기온(실황 T1H, 예보 TMP), 강수확률(POP), 강수형태(PTY), 하늘상태(SKY)뿐이다.
 * 강수량(PCP), 적설(SNO)은 "강수없음" 같은 문자열 범주라 읽지 않는다.
 */
public final class KmaItemConverter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    // +900 이상, -900 이하는 결측이다
    private static final double MISSING_THRESHOLD = 900;

    private KmaItemConverter() {
    }

    // 초단기실황: 발표시각 한 건으로 묶는다. 쓸 값이 하나도 없으면 비어 있다.
    public static List<WeatherForecastData> toObservation(List<KmaResponse.Item> items) {
        Map<LocalDateTime, Values> grouped = new LinkedHashMap<>();
        for (KmaResponse.Item item : items) {
            LocalDateTime at = parseDateTime(item.baseDate(), item.baseTime());
            if (at != null) {
                grouped.computeIfAbsent(at, key -> new Values()).put(item.category(), item.obsrValue());
            }
        }

        return toData(grouped, true);
    }

    // 단기예보: 예보 대상 시각마다 한 건으로 묶는다.
    public static List<WeatherForecastData> toForecasts(List<KmaResponse.Item> items) {
        Map<LocalDateTime, Values> grouped = new LinkedHashMap<>();
        for (KmaResponse.Item item : items) {
            LocalDateTime at = parseDateTime(item.fcstDate(), item.fcstTime());
            if (at != null) {
                grouped.computeIfAbsent(at, key -> new Values()).put(item.category(), item.fcstValue());
            }
        }

        return toData(grouped, false);
    }

    private static List<WeatherForecastData> toData(Map<LocalDateTime, Values> grouped, boolean observed) {
        List<WeatherForecastData> result = new ArrayList<>();
        grouped.forEach((at, values) -> {
            if (values.isEmpty()) {
                return;
            }
            result.add(new WeatherForecastData(at, values.temperature, values.rainProbability,
                    values.precipitationType, values.skyStatus, observed));
        });

        return result;
    }

    private static LocalDateTime parseDateTime(String date, String time) {
        if (date == null || time == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(date + time, DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double number = Double.parseDouble(value.trim());
            return Math.abs(number) >= MISSING_THRESHOLD ? null : number;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Values {
        private Double temperature;
        private Integer rainProbability;
        private Integer precipitationType;
        private Integer skyStatus;

        private void put(String category, String value) {
            Double number = parseNumber(value);
            if (category == null || number == null) {
                return;
            }
            switch (category) {
                case "T1H", "TMP" -> temperature = number;
                case "POP" -> rainProbability = (int) Math.round(number);
                case "PTY" -> precipitationType = (int) Math.round(number);
                case "SKY" -> skyStatus = (int) Math.round(number);
                default -> {
                }
            }
        }

        private boolean isEmpty() {
            return temperature == null && rainProbability == null && precipitationType == null && skyStatus == null;
        }
    }
}
