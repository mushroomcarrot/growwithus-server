package community.likelion.growwithusserver.caretaker.service;

import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.ai.TtsClient;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TtsService {
    private final TtsClient client;
    private record Key(String text, double speed) {}
    private final Map<Key, byte[]> cache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, byte[]> eldest) { return size() > 32; }
    };
    public TtsService(TtsClient client) { this.client = client; }
    public byte[] synthesize(String text, Double speed) {
        double rate = speed == null ? 0.9 : speed;
        if (text == null || text.isBlank() || text.length() > 300 || !Double.isFinite(rate) || rate < 0.5 || rate > 1.5) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        Key key = new Key(text, rate);
        synchronized (cache) {
            byte[] found = cache.get(key);
            if (found != null) return found.clone();
        }
        byte[] audio = client.synthesize(text, rate);
        if (audio == null || audio.length == 0 || audio.length > 1024 * 1024) throw new AiClientException();
        synchronized (cache) { cache.put(key, audio.clone()); }
        return audio.clone();
    }
}
