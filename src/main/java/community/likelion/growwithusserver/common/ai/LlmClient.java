package community.likelion.growwithusserver.common.ai;

// LLM 호출 공통 인터페이스. 실패하면 AiClientException을 던진다.
public interface LlmClient {
    /**
     * @param systemPrompt 역할과 출력 형식 지시
     * @param userPrompt   입력 데이터
     * @return 모델이 생성한 텍스트
     */
    String complete(String systemPrompt, String userPrompt);
}
