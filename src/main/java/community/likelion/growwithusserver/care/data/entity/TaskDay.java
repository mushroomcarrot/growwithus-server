package community.likelion.growwithusserver.care.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "care_task_day", uniqueConstraints = @UniqueConstraint(name = "uk_task_day_caretaker_date",
        columnNames = {"caretaker_username", "task_date"}))
public class TaskDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "caretaker_username", nullable = false, length = 20) private String caretakerUsername;
    @Column(name = "task_date", nullable = false) private LocalDate date;
}
