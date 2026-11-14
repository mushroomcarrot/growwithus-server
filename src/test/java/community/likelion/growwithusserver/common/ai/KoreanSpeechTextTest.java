package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KoreanSpeechTextTest {
    private static void reads(String expected, String text) { assertEquals(expected, KoreanSpeechText.normalize(text)); }

    @Test void readsPlotCodesAsLetterNumberAndZone() {
        reads("비 십삼 구역 밭, 토마토.", "B-13 밭, 토마토.");
        reads("오늘은 비 십사 구역 텃밭에 지지대를 세워 주세요.", "오늘은 B-14 텃밭에 지지대를 세워 주세요.");
        reads("에이 일 구역, 씨 백오 구역", "A-1, C-105");
        reads("비 십삼 구역에 물 주기", "B-13 구역에 물 주기");
        reads("비 십오 칸", "B-15칸");
    }

    @Test void readsCountersWithNativeNumbersAndTheRestWithSinoNumbers() {
        reads("물을 두 번, 세 개, 스무 포기, 스물한 그루", "물을 2번, 3개, 20포기, 21그루");
        reads("삼 개월 뒤, 십오 분 동안, 이 주마다", "3개월 뒤, 15분 동안, 2주마다");
        reads("첫 번째 줄과 세 번째 줄", "1번째 줄과 3번째 줄");
        reads("십일월 십오일, 유월 육일, 시월 십일", "11월 15일, 6월 6일, 10월 10일");
        reads("이천이십육년에 만이천삼백사십오 원", "2026년에 12,345원");
        reads("일억이천만 원, 만 원, 천십 원", "120000000원, 10000원, 1010원");
    }

    @Test void readsTimesTemperaturesUnitsAndRanges() {
        reads("아홉 시 삼십 분에 시작해 십사 시에 끝나요", "9:30에 시작해 14:00에 끝나요");
        reads("오전 일곱 시에 물을 주세요", "오전 7시에 물을 주세요");
        reads("기온 이십 에서 이십오 도, 습도 육십 퍼센트", "기온 20~25℃, 습도 60%");
        reads("영하 삼 도, 십칠 점 오 도", "-3도, 17.5도");
        reads("비 오 밀리미터, 물 이 리터, 간격 삼십 센티미터", "비 5mm, 물 2L, 간격 30cm");
        reads("공 일 공 번으로 영 개", "010번으로 0개");
    }

    @Test void spellsUppercaseAcronymsAndKeepsPlainKorean() {
        reads("에이아이가 정리했어요. 엑스피 십 획득", "AI가 정리했어요. XP 10 획득");
        reads("오늘 텃밭에 물을 줬어요.", "오늘 텃밭에 물을 줬어요.");
        reads("", "");
    }
}
