package community.likelion.growwithusserver.harvest.data.entity;

import community.likelion.growwithusserver.harvest.data.enums.OutingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 처인 나들이 추천 장소 (체험농장, 식당)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outing")
public class Outing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutingType type;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
