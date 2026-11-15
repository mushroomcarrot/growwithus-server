package community.likelion.growwithusserver.diary.data.repository;
import community.likelion.growwithusserver.diary.data.entity.DiaryEntry;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface DiaryRepository extends JpaRepository<DiaryEntry, Long> {
    java.util.Optional<DiaryEntry> findByTypeAndSourceId(DiaryEntry.Type type, Long sourceId);
    @Query("select d from DiaryEntry d where d.gardenId = :gardenId and (:cursor is null or d.id < :cursor) "
            + "and (:type is null or d.type = :type) and (:photoOnly = false or d.photoUrl is not null) order by d.id desc")
    List<DiaryEntry> findPage(@Param("gardenId") Long gardenId, @Param("cursor") Long cursor,
                              @Param("type") DiaryEntry.Type type, @Param("photoOnly") boolean photoOnly, Pageable pageable);
}
