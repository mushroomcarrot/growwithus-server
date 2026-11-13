package community.likelion.growwithusserver.farm;

import community.likelion.growwithusserver.farm.service.KmaGrid;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 기대값은 기상청 격자 엑셀의 값이다 (docs/public-data.md)
class KmaGridTest {

    @Test
    void 처인구_대표_좌표는_64_119() {
        assertThat(KmaGrid.toGrid(37.2315, 127.2038)).containsExactly(64, 119);
    }

    @Test
    void 원삼면은_66_118() {
        assertThat(KmaGrid.toGrid(37.1635, 127.3151)).containsExactly(66, 118);
    }

    @Test
    void 기흥구와_수지구_대표_좌표() {
        assertThat(KmaGrid.toGrid(37.2776, 127.1168)).containsExactly(62, 120);
        assertThat(KmaGrid.toGrid(37.3194, 127.1001)).containsExactly(62, 121);
    }
}
