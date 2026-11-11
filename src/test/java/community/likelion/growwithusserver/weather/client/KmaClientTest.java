package community.likelion.growwithusserver.weather.client;

import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaClientTest {
    private final KmaClient kmaClient = new KmaClient(null, JsonMapper.builder().build(), new KmaProperties());

    @Test
    void 실제_응답_형식을_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"dataType":"JSON","items":{"item":[
                {"baseDate":"20261111","baseTime":"1100","category":"PTY","nx":64,"ny":119,"obsrValue":"0"},
                {"baseDate":"20261111","baseTime":"1100","category":"T1H","nx":64,"ny":119,"obsrValue":"9.2"}
                ]},"pageNo":1,"numOfRows":20,"totalCount":8}}}""";

        KmaResponse.Body parsed = kmaClient.parse(body);

        assertThat(parsed.totalCount()).isEqualTo(8);
        List<WeatherForecastData> observation = KmaItemConverter.toObservation(parsed.items().item());
        assertThat(observation).containsExactly(
                new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 11, 0), 9.2, null, 0, null, true));
    }

    @Test
    void resultCode가_정상이_아니면_실패한다() {
        String body = """
                {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}""";

        assertThatThrownBy(() -> kmaClient.parse(body)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 인증키_오류처럼_XML로_온_응답은_실패한다() {
        String body = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";

        assertThatThrownBy(() -> kmaClient.parse(body)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void 인증키는_인코딩된_값과_디코딩된_값_모두_한_번만_인코딩한다() {
        assertThat(KmaClient.encodeServiceKey("ab%2Bcd%2Fef%3D%3D")).isEqualTo("ab%2Bcd%2Fef%3D%3D");
        assertThat(KmaClient.encodeServiceKey("ab+cd/ef==")).isEqualTo("ab%2Bcd%2Fef%3D%3D");
    }
}
