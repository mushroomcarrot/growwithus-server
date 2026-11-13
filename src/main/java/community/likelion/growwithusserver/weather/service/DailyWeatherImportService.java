package community.likelion.growwithusserver.weather.service;
import community.likelion.growwithusserver.weather.data.repository.DailyWeatherRepository;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DailyWeatherImportService {
    private final DailyWeatherRepository days;
    public record Day(LocalDate date, double avgTemp, double minTemp, double maxTemp) {
        public Day {
            if (date == null || date.getYear() < 1000 || !date.isBefore(LocalDate.now())
                    || !Double.isFinite(avgTemp) || !Double.isFinite(minTemp) || !Double.isFinite(maxTemp)
                    || minTemp < -80 || maxTemp > 60 || minTemp > avgTemp || avgTemp > maxTemp) {
                throw new IllegalArgumentException("지난 날짜와 기온 범위를 확인해 주세요.");
            }
        }
    }
    public record ImportRequest(int nx, int ny, String sourceUrl, List<Day> days) {
        public ImportRequest {
            if (nx < 1 || nx > 300 || ny < 1 || ny > 300 || sourceUrl == null || sourceUrl.length() > 512
                    || days == null || days.isEmpty() || days.size() > 366) throw new IllegalArgumentException("기상 자료 범위를 확인해 주세요.");
            URI source = URI.create(sourceUrl);
            if (!"https".equals(source.getScheme()) || source.getHost() == null || source.getRawUserInfo() != null) {
                throw new IllegalArgumentException("기상 자료의 출처 주소를 확인해 주세요.");
            }
            days = List.copyOf(days);
            Set<LocalDate> dates = new HashSet<>();
            for (Day day : days) if (!dates.add(day.date())) throw new IllegalArgumentException("같은 날짜가 두 번 있어요.");
        }
    }
    public record Imported(int importedDays) {}
    @Transactional
    public Imported importDays(ImportRequest request) {
        if (request == null) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        for (Day day : request.days()) {
            days.upsertObservation(request.nx(), request.ny(), day.date(), day.avgTemp(), day.minTemp(), day.maxTemp(),
                    request.sourceUrl(), LocalDateTime.now());
        }
        return new Imported(request.days().size());
    }
}
