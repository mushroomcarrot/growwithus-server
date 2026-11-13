package community.likelion.growwithusserver.farm.data.entity;

import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 작물과 생육 기준. 성장 시뮬레이션이 기준온도와 단계별 적산온도를 쓴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crop")
public class Crop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    // 작물 캐릭터의 기본 이름 (예: 토순이)
    @Column(nullable = false, length = 20)
    private String characterName;

    // 유효적산온도의 기준온도 (℃)
    @Column(nullable = false)
    private double baseTemp;

    // 각 단계에 들어서는 데 필요한 누적 유효적산온도. SEED는 항상 0이라 컬럼을 두지 않는다
    @Column(nullable = false)
    private double sproutGdd;

    @Column(nullable = false)
    private double flowerGdd;

    @Column(nullable = false)
    private double fruitGdd;

    // 재배 일정 설명 (예: 아주심기 뒤 60~70일부터 수확)
    @Column(length = 300)
    private String schedule;

    // 기준 값의 출처
    @Column(length = 200)
    private String source;

    public double requiredGdd(GrowthStage stage) {
        return switch (stage) {
            case SEED -> 0;
            case SPROUT -> sproutGdd;
            case FLOWER -> flowerGdd;
            case FRUIT -> fruitGdd;
        };
    }
}
