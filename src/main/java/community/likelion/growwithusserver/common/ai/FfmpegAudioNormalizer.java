package community.likelion.growwithusserver.common.ai;

import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FfmpegAudioNormalizer {
    private static final int MAX_PCM = 60 * 16000 * 2;
    public byte[] normalize(byte[] audio) {
        if (audio == null || audio.length == 0 || audio.length > 20 * 1024 * 1024) throw new FileNotSupportedException();
        Path input = null, output = null;
        try {
            input = Files.createTempFile("grow-audio-", ".input"); output = Files.createTempFile("grow-audio-", ".pcm");
            Files.write(input, audio);
            int code = AudioProcesses.run(List.of("ffmpeg", "-y", "-nostdin", "-v", "error", "-i", input.toString(),
                    "-vn", "-t", "61", "-ar", "16000", "-ac", "1", "-f", "s16le", output.toString()));
            long length = Files.size(output);
            if (code != 0 || length == 0 || length > MAX_PCM || length % 2 != 0) throw new FileNotSupportedException();
            return wave(Files.readAllBytes(output));
        } catch (java.io.IOException ex) { throw new AiClientException(); }
        finally { AudioProcesses.cleanup(input, output); }
    }
    static byte[] wave(byte[] pcm) {
        var out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return out.array();
    }
}
