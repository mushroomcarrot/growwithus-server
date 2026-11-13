package community.likelion.growwithusserver.common.ai;

// 음성 인식(STT) 공통 인터페이스. 실패하면 AiClientException을 던진다.
public interface SttClient {
    /**
     * @param audio    공통 파일 검증을 통과한 음성 내용 (webm, m4a, mp4, wav)
     * @param filename 확장자를 포함한 파일 이름
     * @return 받아 적은 텍스트
     */
    String transcribe(byte[] audio, String filename);
}
