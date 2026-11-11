package community.likelion.growwithusserver.common.pagination;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

// 커서 페이지네이션 응답. nextCursor가 null이면 마지막 페이지다.
@Getter
@AllArgsConstructor
public class CursorPageResponse<T> {
    private final List<T> items;
    private final String nextCursor;

    /**
     * limit + 1건을 조회한 결과로 응답을 만든다.
     * 조회 결과가 limit보다 많으면 다음 페이지가 있다고 보고, 이번 페이지 마지막 항목의 ID를 nextCursor로 준다.
     *
     * @param fetched  limit + 1건까지 조회한 목록 (정렬된 상태)
     * @param limit    요청한 페이지 크기
     * @param cursorOf 항목에서 커서(외부 ID)를 꺼내는 함수
     */
    public static <T> CursorPageResponse<T> of(List<T> fetched, int limit, Function<T, String> cursorOf) {
        if (fetched.size() <= limit) {
            return new CursorPageResponse<>(List.copyOf(fetched), null);
        }

        List<T> items = List.copyOf(fetched.subList(0, limit));
        return new CursorPageResponse<>(items, cursorOf.apply(items.get(items.size() - 1)));
    }

    // 항목만 다른 타입(DTO)으로 바꾼다
    public <R> CursorPageResponse<R> map(Function<T, R> mapper) {
        return new CursorPageResponse<>(items.stream().map(mapper).toList(), nextCursor);
    }
}
