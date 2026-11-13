package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.farm.data.dto.PlotPreviewResponseDto;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GardenHomeResponseDto {
    private String gardenId;
    private CharacterDto character;
    // 수집된 날씨가 없으면 null
    private CurrentWeatherDto weather;
    private StreakDto streak;
    private XpDto xp;
    private HarvestDto harvest;
    // 담당 관리사. 배정 전이면 null
    private PlotPreviewResponseDto.CaretakerSummary caretaker;
    // 가장 최근 돌봄 기록. 없으면 null
    private RecentCareLogDto recentCareLog;
    private boolean hasUnreadEvent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterDto {
        private String name;
        private GrowthStage stage;
        private int level;
        private CharacterMood mood;
        private String animation;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreakDto {
        private int days;
        private boolean cheeredToday;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XpDto {
        private int total;
        private int today;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HarvestDto {
        // 성장 시뮬레이션이 계산하기 전이면 둘 다 null. 수확일이 지났으면 dDay는 0
        private LocalDate expectedDate;
        private Integer dDay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCareLogDto {
        private String careLogId;
        private String summary;
        private String photoUrl;
        private OffsetDateTime createdAt;
        private boolean hearted;
    }
}
