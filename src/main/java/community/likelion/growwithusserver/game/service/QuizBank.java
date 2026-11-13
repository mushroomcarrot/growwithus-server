package community.likelion.growwithusserver.game.service;

import java.util.List;

// 오늘의 텃밭 퀴즈 문제 은행. 문제를 더하려면 목록 끝에 붙인다 (순번이 garden_quiz에 저장되므로 중간에 끼우지 않는다).
public final class QuizBank {
    private QuizBank() {
    }

    public record Quiz(String question, List<String> choices, int answerIndex, String explanation) {
    }

    private static final List<Quiz> QUIZZES = List.of(
            new Quiz("토마토 곁순은 왜 따 줄까요?",
                    List.of("열매에 영양을 모으려고", "물을 덜 주려고", "벌레를 부르려고"), 0,
                    "곁순을 따면 열매가 더 커져요."),
            new Quiz("토마토에 지지대를 세우는 까닭은 무엇일까요?",
                    List.of("햇빛을 가리려고", "줄기가 쓰러지지 않게 하려고", "흙을 따뜻하게 하려고"), 1,
                    "열매가 달리면 줄기가 무거워져서 지지대에 묶어 줘요."),
            new Quiz("텃밭에 물은 언제 주는 것이 좋을까요?",
                    List.of("한낮", "해가 진 한밤중", "아침 일찍"), 2,
                    "아침에 주면 낮 동안 물을 잘 쓰고 잎도 빨리 말라요."),
            new Quiz("토마토 꽃은 무슨 색일까요?",
                    List.of("노란색", "빨간색", "보라색"), 0,
                    "노란 꽃이 지고 나면 그 자리에 열매가 맺혀요."),
            new Quiz("방울토마토가 다 익으면 어떻게 될까요?",
                    List.of("초록색으로 단단해져요", "빨갛게 물들어요", "하얗게 변해요"), 1,
                    "초록색 열매가 햇빛을 받으며 빨갛게 익어요."),
            new Quiz("비가 많이 온 다음 날 텃밭에서는 무엇을 살펴야 할까요?",
                    List.of("물이 잘 빠지는지", "물을 더 줄지", "씨앗을 새로 뿌릴지"), 0,
                    "물이 고이면 뿌리가 숨을 못 쉬어서 물길을 터 줘요."),
            new Quiz("작물 둘레에 풀이 많으면 어떻게 될까요?",
                    List.of("작물이 더 빨리 커요", "열매가 더 달아져요", "작물이 먹을 양분을 풀이 가져가요"), 2,
                    "그래서 관리사 선생님이 풀을 뽑아 주세요."));

    public static int size() {
        return QUIZZES.size();
    }

    public static Quiz get(int index) {
        return QUIZZES.get(Math.floorMod(index, QUIZZES.size()));
    }
}
