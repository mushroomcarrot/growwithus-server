package community.likelion.growwithusserver.harvest.service;

// 이름 뒤에 붙는 조사를 받침에 맞게 고른다 (토순이가, 작물이 / 토순이를, 작물을).
final class KoreanParticle {
    private static final char HANGUL_START = '가';
    private static final char HANGUL_END = '힣';
    private static final int FINAL_CONSONANT_CYCLE = 28;

    private KoreanParticle() {
    }

    static String subject(String word) {
        return word + (hasFinalConsonant(word) ? "이" : "가");
    }

    static String object(String word) {
        return word + (hasFinalConsonant(word) ? "을" : "를");
    }

    private static boolean hasFinalConsonant(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) {
            return false;
        }
        return (last - HANGUL_START) % FINAL_CONSONANT_CYCLE != 0;
    }
}
