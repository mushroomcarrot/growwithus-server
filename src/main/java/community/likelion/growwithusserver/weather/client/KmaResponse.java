package community.likelion.growwithusserver.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 기상청 단기예보 조회서비스의 JSON 응답. 초단기실황과 단기예보가 같은 틀을 쓴다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {
    }

    /**
     * 한 줄이 시각 하나의 요소 하나다.
     *
     * @param obsrValue 실황 값. 초단기실황에만 있다
     * @param fcstDate  예보 대상 날짜 (yyyyMMdd). 예보에만 있다
     * @param fcstTime  예보 대상 시각 (HHmm). 예보에만 있다
     * @param fcstValue 예보 값. 예보에만 있다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String baseDate, String baseTime, String category,
                       String obsrValue, String fcstDate, String fcstTime, String fcstValue) {
    }
}
