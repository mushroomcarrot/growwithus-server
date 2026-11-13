package community.likelion.growwithusserver.harvest.garden;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 수확, 방문 예약이 텃밭 도메인에서 필요로 하는 값만 모은 조회 결과. HarvestGardenReader 구현체가 채운다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarvestGarden {
    private community.likelion.growwithusserver.farm.data.enums.GrowthStage stage;
    private Long gardenId;
    // 텃밭 주인의 로그인 아이디 (내 텃밭인지 확인)
    private String ownerUsername;
    // 주인 닉네임 (예: 민지네). 관리사 알림 문구에 쓴다
    private String ownerNickname;
    @Builder.Default private int familySize = 1;
    // 작물 캐릭터 이름 (예: 토순이)
    private String characterName;
    private Long farmId;
    // 농장 위치. 나들이 추천의 거리 계산에 쓴다. 없으면 거리 없이 내려준다
    private Double farmLatitude;
    private Double farmLongitude;
    // 담당 관리사의 로그인 아이디와 성함. 아직 배정 전이면 null
    private String caretakerUsername;
    private String caretakerName;
    // 텃밭을 시작한 날 (함께 돌본 일수 계산)
    private LocalDate startedOn;
    // 성장 시뮬레이션이 예측한 수확일. 아직 계산 전이면 null
    private LocalDate expectedHarvestDate;
    // 지금 수확할 수 있는지 (열매 단계 도달)
    private boolean harvestable;
    // 지금까지의 돌봄 기록 수
    private int careLogCount;
}
