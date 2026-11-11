package community.likelion.growwithusserver.common.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfiguration {

    // 외부 API(기상청, 공공데이터포털, 농사로, LLM 등) 호출용 공통 클라이언트.
    // 도메인 클라이언트는 externalRestClient.mutate().baseUrl(...).build()로 파생해서 쓴다.
    @Bean
    public RestClient externalRestClient(@Value("${external.http.connect-timeout}") Duration connectTimeout,
                                         @Value("${external.http.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
