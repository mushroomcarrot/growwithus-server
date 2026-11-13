package community.likelion.growwithusserver.caretaker;

import community.likelion.growwithusserver.caretaker.service.CaretakerService;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.care.data.repository.CareLogRepository;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CaretakerSummaryTest {
    @Test void 과거_월을_조회해도_오늘_하트는_오늘의_수신_시간으로_집계한다() {
        var profiles = mock(CaretakerProfileRepository.class);
        var logs = mock(CareLogRepository.class);
        var hearts = mock(CareLogHeartRepository.class);
        var service = new CaretakerService(null, profiles, null, logs, hearts);
        when(profiles.findByUsername("care")).thenReturn(Optional.of(CaretakerProfile.builder().build()));
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today).minusMonths(1);
        when(hearts.countByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                "care", month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay())).thenReturn(12L);
        when(hearts.countByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                "care", today.atStartOfDay(), today.plusDays(1).atStartOfDay())).thenReturn(3L);
        var response = service.summary("care", month.toString()).getData();
        assertThat(response.getHeartsReceived()).isEqualTo(12);
        assertThat(response.getHeartsReceivedToday()).isEqualTo(3);
    }
}
