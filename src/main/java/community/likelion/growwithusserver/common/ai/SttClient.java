package community.likelion.growwithusserver.common.ai;

// 음성 인식(STT) 공통 인터페이스. 실패하면 AiClientException을 던진다.
public interface SttClient {
    /**
     * @param audio    음성 파일 내용 (m4a, wav)
     * @param filename 확장자를 포함한 파일 이름
     * @return 받아 적은 텍스트
     */
    String transcribe(byte[] audio, String filename);
}
