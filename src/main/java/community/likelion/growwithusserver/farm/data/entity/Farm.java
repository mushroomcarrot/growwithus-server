package community.likelion.growwithusserver.farm.data.entity;

import community.likelion.growwithusserver.farm.data.enums.District;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 주말농장. 공공데이터의 농장은 FarmSyncService가, 시연 농장은 FarmSeeder가 넣는다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "farm")
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private District district;

    // 기상청 격자. 좌표에서 KmaGrid로 구한다
    @Column(nullable = false)
    private int nx;

    @Column(nullable = false)
    private int ny;

    // 원본 데이터의 식별자 (예: yongin-weekend-farm:양달농원, 시연 농장은 demo:theme). 동기화가 같은 농장을 찾는 기준
    @Column(nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
