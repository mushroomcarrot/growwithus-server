package community.likelion.growwithusserver.harvest.data.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** 예약이 없는 날에도 같은 농장의 예약 처리를 직렬화하는 잠금 행. */
@Entity @Getter @NoArgsConstructor
@Table(name = "visit_booking_day", uniqueConstraints = @UniqueConstraint(name = "uk_visit_booking_farm_date", columnNames = {"farm_id", "visit_date"}))
public class VisitBookingDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "visit_date", nullable = false) private LocalDate date;
}
