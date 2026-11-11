package community.likelion.growwithusserver.common.pagination;

import community.likelion.growwithusserver.common.id.IdPrefix;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

// 커서 페이지네이션 요청 (쿼리 cursor, limit). 컨트롤러에서 @Valid @ModelAttribute로 받는다.
@Data
public class CursorPageRequest {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    // 이전 응답의 nextCursor. 첫 페이지는 생략한다.
    private String cursor;

    @Min(1)
    @Max(MAX_LIMIT)
    private Integer limit;

    public int getLimit() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    // 다음 페이지가 있는지 알기 위해 한 건 더 조회할 때 쓰는 크기
    public int getFetchSize() {
        return getLimit() + 1;
    }

    // cursor를 PK로 바꾼다. 첫 페이지면 null, 형식이 틀리면 C002.
    public Long getCursorId(IdPrefix prefix) {
        return cursor == null || cursor.isBlank() ? null : prefix.parse(cursor);
    }
}
