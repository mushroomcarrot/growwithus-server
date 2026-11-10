package community.likelion.growwithusserver.common.ai;

// 음성 합성(TTS) 공통 인터페이스. 실패하면 AiClientException을 던진다.
public interface TtsClient {
    /**
     * @param text 읽어 줄 문장
     * @return audio/mpeg 형식의 음성
     */
    byte[] synthesize(String text);
}
