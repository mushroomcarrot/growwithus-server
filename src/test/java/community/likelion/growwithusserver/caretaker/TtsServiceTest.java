package community.likelion.growwithusserver.caretaker;

import community.likelion.growwithusserver.caretaker.service.TtsService;
import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.ai.TtsClient;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TtsServiceTest {
    @Test void cachesSuccessfulAudioAndCopiesBothBoundaries() {
        var calls = new AtomicInteger();
        var source = new byte[]{1, 2, 3};
        var service = new TtsService(text -> { calls.incrementAndGet(); return source; });
        var first = service.synthesize("hello", null);
        first[0] = 9; source[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, service.synthesize("hello", 0.9));
        assertEquals(1, calls.get());
    }
    @Test void doesNotCacheFailuresOrInventUnsupportedSpeed() {
        var calls = new AtomicInteger();
        TtsClient client = text -> { calls.incrementAndGet(); throw new AiClientException(); };
        var service = new TtsService(client);
        assertThrows(AiClientException.class, () -> service.synthesize("hello", null));
        assertThrows(AiClientException.class, () -> service.synthesize("hello", null));
        assertEquals(2, calls.get());
        assertThrows(AiClientException.class, () -> service.synthesize("hello", 1.2));
        assertEquals(2, calls.get());
    }
    @Test void rejectsInvalidInputsBeforeProviderCall() {
        var service = new TtsService(text -> { fail("invalid input reached provider"); return null; });
        assertThrows(VoiceMemoException.class, () -> service.synthesize(" ", null));
        assertThrows(VoiceMemoException.class, () -> service.synthesize("a".repeat(301), null));
        for (double rate : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0.49, 1.51}) {
            assertThrows(VoiceMemoException.class, () -> service.synthesize("hello", rate));
        }
    }
    @Test void boundsCacheAndSeparatesPlaybackSpeeds() {
        var calls = new AtomicInteger();
        TtsClient client = new TtsClient() {
            public byte[] synthesize(String text) { return new byte[]{1}; }
            public byte[] synthesize(String text, double speed) { calls.incrementAndGet(); return new byte[]{1}; }
        };
        var service = new TtsService(client);
        service.synthesize("hello", 0.9); service.synthesize("hello", 1.0);
        assertEquals(2, calls.get());
        for (int i = 0; i < 32; i++) service.synthesize("text" + i, null);
        service.synthesize("hello", 0.9);
        assertEquals(35, calls.get());
    }
}
