package community.likelion.growwithusserver.ai.logic;

import java.util.List;

/** 음성 원문은 관찰 기록이다. 모델 출력만으로 작업을 완료하거나 병을 진단하지 않는다. */
public final class VoiceAnalysis {
    private VoiceAnalysis() {}

    public enum Issue {
        LEAF_SPOT("잎에 점이 있어요"),
        YELLOW_LEAF("잎이 노랗게 보여요"),
        WILTING("작물이 시들어 보여요"),
        PEST_SUSPECTED("벌레가 보인다는 기록이 있어요"),
        STEM_DAMAGE("줄기가 손상된 것 같아요");

        private final String label;
        Issue(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Result(String transcript, String summary, List<Issue> issues, boolean fallbackUsed) {
        public Result {
            if (transcript == null || transcript.isBlank() || transcript.length() > 8000
                    || summary == null || summary.isBlank() || summary.length() > 1000) {
                throw new IllegalArgumentException("음성 정리 결과가 유효하지 않습니다.");
            }
            issues = List.copyOf(issues);
        }
    }
}
