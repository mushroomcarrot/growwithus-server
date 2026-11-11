package community.likelion.growwithusserver.weather.client;

import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import community.likelion.growwithusserver.weather.data.exception.KmaApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 기상청 단기예보 조회서비스 클라이언트 (공공데이터포털). 명세는 docs/public-data.md를 본다.
 *
 * 실패하면 설정한 횟수만큼 다시 시도하고, 끝까지 실패하면 KmaApiException을 던진다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(KmaProperties.class)
public class KmaClient {
    private static final String RESULT_OK = "00";
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");
    // 단기예보 한 번 발표가 900줄을 넘는다. 넘치면 다음 쪽을 더 받는다
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 5;

    private final RestClient externalRestClient;
    private final ObjectMapper objectMapper;
    private final KmaProperties kmaProperties;

    public KmaClient(RestClient externalRestClient, ObjectMapper objectMapper, KmaProperties kmaProperties) {
        this.externalRestClient = externalRestClient;
        this.objectMapper = objectMapper;
        this.kmaProperties = kmaProperties;
    }

    public boolean isConfigured() {
        return kmaProperties.isConfigured();
    }

    // 초단기실황: 지금 받을 수 있는 가장 최근 관측값 한 건
    public List<WeatherForecastData> fetchObservation(int nx, int ny, LocalDateTime now) {
        List<KmaResponse.Item> items = fetchAll("getUltraSrtNcst", KmaBaseTime.observation(now), nx, ny);

        return KmaItemConverter.toObservation(items);
    }

    // 단기예보: 지금 받을 수 있는 가장 최근 발표분의 시간대별 예보
    public List<WeatherForecastData> fetchForecasts(int nx, int ny, LocalDateTime now) {
        List<KmaResponse.Item> items = fetchAll("getVilageFcst", KmaBaseTime.forecast(now), nx, ny);

        return KmaItemConverter.toForecasts(items);
    }

    private List<KmaResponse.Item> fetchAll(String operation, LocalDateTime baseTime, int nx, int ny) {
        List<KmaResponse.Item> items = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            KmaResponse.Body body = fetchPage(operation, baseTime, nx, ny, pageNo);
            if (body.items() != null && body.items().item() != null) {
                items.addAll(body.items().item());
            }
            if (body.totalCount() == null || items.size() >= body.totalCount()) {
                break;
            }
        }

        return items;
    }

    private KmaResponse.Body fetchPage(String operation, LocalDateTime baseTime, int nx, int ny, int pageNo) {
        URI uri = buildUri(operation, baseTime, nx, ny, pageNo);
        int maxAttempts = Math.max(1, kmaProperties.getMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseBody = externalRestClient.get().uri(uri).retrieve().body(String.class);

                return parse(responseBody);
            } catch (Exception e) {
                // 인증키가 주소에 들어 있으므로 주소는 남기지 않는다
                log.warn("[fetchPage] 기상청 호출 실패: {} 격자 ({}, {}), {}번째 시도, {}",
                        operation, nx, ny, attempt, describe(e));
                if (attempt < maxAttempts) {
                    waitBeforeRetry();
                }
            }
        }

        throw new KmaApiException();
    }

    // 인증키 오류처럼 게이트웨이가 거부한 요청은 JSON을 요청해도 XML로 온다. 그때는 읽지 못해 실패로 처리된다.
    KmaResponse.Body parse(String responseBody) {
        KmaResponse parsed = objectMapper.readValue(responseBody, KmaResponse.class);
        if (parsed == null || parsed.response() == null || parsed.response().header() == null) {
            throw new IllegalStateException("응답 형식이 다름");
        }

        KmaResponse.Header header = parsed.response().header();
        if (!RESULT_OK.equals(header.resultCode())) {
            throw new IllegalStateException("resultCode " + header.resultCode() + " " + header.resultMsg());
        }
        if (parsed.response().body() == null) {
            throw new IllegalStateException("본문이 비어 있음");
        }

        return parsed.response().body();
    }

    // 인증키는 한 번만 인코딩해 붙이고, 완성된 URI를 그대로 넘겨 다시 인코딩되지 않게 한다
    private URI buildUri(String operation, LocalDateTime baseTime, int nx, int ny, int pageNo) {
        return URI.create(kmaProperties.getBaseUrl() + "/" + operation
                + "?serviceKey=" + encodeServiceKey(kmaProperties.getServiceKey())
                + "&pageNo=" + pageNo
                + "&numOfRows=" + PAGE_SIZE
                + "&dataType=JSON"
                + "&base_date=" + baseTime.format(BASE_DATE)
                + "&base_time=" + baseTime.format(BASE_TIME)
                + "&nx=" + nx
                + "&ny=" + ny);
    }

    // 포털이 보여주는 키는 이미 인코딩되어 있다 (%2B, %2F, %3D). 디코딩된 키를 넣어도 같게 동작하도록 맞춘다.
    static String encodeServiceKey(String serviceKey) {
        String trimmed = serviceKey.trim();
        String decoded = trimmed.contains("%") ? URLDecoder.decode(trimmed, StandardCharsets.UTF_8) : trimmed;

        return URLEncoder.encode(decoded, StandardCharsets.UTF_8);
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(kmaProperties.getRetryDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KmaApiException();
        }
    }

    // 예외 메시지에 주소가 섞여 인증키가 로그에 남지 않도록 종류만 남긴다. 직접 만든 메시지는 그대로 쓴다.
    private String describe(Exception e) {
        return e instanceof IllegalStateException ? e.getMessage() : e.getClass().getSimpleName();
    }
}
