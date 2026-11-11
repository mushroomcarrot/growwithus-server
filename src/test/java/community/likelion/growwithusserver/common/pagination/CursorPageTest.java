package community.likelion.growwithusserver.common.pagination;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.id.InvalidIdException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPageTest {

    @Test
    void 조회_결과가_limit보다_많으면_잘라내고_마지막_항목을_커서로_준다() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(9L, 8L, 7L), 2, IdPrefix.CARE_LOG::format);

        assertThat(page.getItems()).containsExactly(9L, 8L);
        assertThat(page.getNextCursor()).isEqualTo("cl_8");
    }

    @Test
    void 조회_결과가_limit_이하면_마지막_페이지다() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(9L, 8L), 2, IdPrefix.CARE_LOG::format);

        assertThat(page.getItems()).containsExactly(9L, 8L);
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void map은_커서를_유지하고_항목만_바꾼다() {
        CursorPageResponse<String> page = CursorPageResponse.of(List.of(9L, 8L, 7L), 2, IdPrefix.CARE_LOG::format)
                .map(id -> "item" + id);

        assertThat(page.getItems()).containsExactly("item9", "item8");
        assertThat(page.getNextCursor()).isEqualTo("cl_8");
    }

    @Test
    void 요청_기본값과_커서_변환() {
        CursorPageRequest request = new CursorPageRequest();

        assertThat(request.getLimit()).isEqualTo(20);
        assertThat(request.getFetchSize()).isEqualTo(21);
        assertThat(request.getCursorId(IdPrefix.CARE_LOG)).isNull();

        request.setCursor("cl_8");
        request.setLimit(5);
        assertThat(request.getCursorId(IdPrefix.CARE_LOG)).isEqualTo(8L);
        assertThat(request.getFetchSize()).isEqualTo(6);
    }

    @Test
    void 접두어가_다르거나_숫자가_아닌_ID는_거부한다() {
        assertThat(IdPrefix.USER.format(12L)).isEqualTo("usr_12");
        assertThat(IdPrefix.USER.parse("usr_12")).isEqualTo(12L);

        assertThatThrownBy(() -> IdPrefix.USER.parse("gdn_12")).isInstanceOf(InvalidIdException.class);
        assertThatThrownBy(() -> IdPrefix.USER.parse("usr_minji")).isInstanceOf(InvalidIdException.class);
        assertThatThrownBy(() -> IdPrefix.USER.parse("usr_0")).isInstanceOf(InvalidIdException.class);
        assertThatThrownBy(() -> IdPrefix.USER.parse(null)).isInstanceOf(InvalidIdException.class);
    }
}
