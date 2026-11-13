package community.likelion.growwithusserver.farm.data.entity;

import community.likelion.growwithusserver.user.data.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 농장의 칸. 칸마다 심은 작물과 담당 관리사가 정해져 있고, 주인 한 명에게만 분양된다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plot", uniqueConstraints = @UniqueConstraint(name = "uk_plot_farm_code", columnNames = {"farm_id", "plot_code"}))
public class Plot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    // 칸 번호 (예: B-14)
    @Column(name = "plot_code", nullable = false, length = 20)
    private String plotCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id", nullable = false)
    private Crop crop;

    // 담당 관리사. 아직 배정 전이면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caretaker_user_id")
    private User caretaker;

    // 분양 여부. 주인 프로필을 등록할 때 true로 바뀐다
    @Builder.Default
    @Column(nullable = false)
    private boolean assigned = false;
}
