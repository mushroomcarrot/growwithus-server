package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;

/** 실제 AI 결과를 저장, 확정, 일기 연결용 조회로 전달한다. 저장소는 테스트 대역이다. */
public final class CloudVoiceFlowCheck {
    private CloudVoiceFlowCheck() {}
    public static void confirm(VoiceAnalysis.Result result) {
        var fixture = new VoiceMemoSelfTest.Fixture();
        var attempt = fixture.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/public.wav");
        fixture.store.finish("caretaker", attempt, result);
        var confirmed = fixture.service.confirm("caretaker", "vm_1", null).getData();
        var linked = fixture.store.getConfirmedForTask("caretaker", 1L, 1L);
        if (confirmed.getStatus() != VoiceMemoStatus.CONFIRMED
                || !linked.transcript().equals(result.transcript()) || !linked.summary().equals(result.summary())
                || !linked.issues().equals(result.issues().stream().map(Enum::name).toList())) {
            throw new IllegalStateException("Confirmed memo did not preserve the AI result");
        }
    }
}
