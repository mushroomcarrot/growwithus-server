package community.likelion.growwithusserver.common.file;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

// wav, m4a(mp4) 파일 헤더에서 재생 길이를 읽는다. 읽을 수 없으면 empty.
public final class AudioDurationReader {
    private AudioDurationReader() {
    }

    // RIFF/WAVE: 길이 = data 청크 크기 / fmt 청크의 초당 바이트 수
    public static Optional<Duration> readWav(byte[] bytes) {
        if (bytes.length < 12 || !tag(bytes, 0).equals("RIFF") || !tag(bytes, 8).equals("WAVE")) {
            return Optional.empty();
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long byteRate = 0;
        int position = 12;

        while (position + 8 <= bytes.length) {
            String chunkId = tag(bytes, position);
            long chunkSize = Integer.toUnsignedLong(buffer.getInt(position + 4));
            int body = position + 8;

            if (chunkId.equals("fmt ") && body + 12 <= bytes.length) {
                byteRate = Integer.toUnsignedLong(buffer.getInt(body + 8));
            } else if (chunkId.equals("data")) {
                if (byteRate <= 0) {
                    return Optional.empty();
                }
                // 스트리밍으로 만든 파일은 크기가 실제보다 크게 적힌 경우가 있어 남은 길이로 제한한다
                long dataSize = Math.min(chunkSize, bytes.length - (long) body);
                return Optional.of(Duration.ofMillis(dataSize * 1000 / byteRate));
            }

            // 청크는 짝수 바이트로 정렬된다
            long next = body + chunkSize + (chunkSize % 2);
            if (next > bytes.length) {
                break;
            }
            position = (int) next;
        }

        return Optional.empty();
    }

    // MP4(m4a): moov 박스 안 mvhd 박스의 duration / timescale
    public static Optional<Duration> readM4a(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int[] moov = findBox(buffer, 0, bytes.length, "moov");
        if (moov == null) {
            return Optional.empty();
        }

        int[] mvhd = findBox(buffer, moov[0], moov[1], "mvhd");
        if (mvhd == null) {
            return Optional.empty();
        }

        int body = mvhd[0];
        int end = mvhd[1];
        if (body + 4 > end) {
            return Optional.empty();
        }

        int version = bytes[body] & 0xFF;
        long timescale;
        long duration;
        if (version == 1) {
            if (body + 32 > end) {
                return Optional.empty();
            }
            timescale = Integer.toUnsignedLong(buffer.getInt(body + 20));
            duration = buffer.getLong(body + 24);
        } else {
            if (body + 20 > end) {
                return Optional.empty();
            }
            timescale = Integer.toUnsignedLong(buffer.getInt(body + 12));
            duration = Integer.toUnsignedLong(buffer.getInt(body + 16));
        }

        if (timescale <= 0 || duration < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(duration * 1000 / timescale));
    }

    // [start, end) 범위에서 type 박스를 찾아 {본문 시작, 박스 끝}을 돌려준다. 없으면 null.
    private static int[] findBox(ByteBuffer buffer, int start, int end, String type) {
        int position = start;

        while (position + 8 <= end) {
            long size = Integer.toUnsignedLong(buffer.getInt(position));
            String boxType = tag(buffer.array(), position + 4);
            int headerSize = 8;

            if (size == 1) {
                // 64비트 크기
                if (position + 16 > end) {
                    return null;
                }
                size = buffer.getLong(position + 8);
                headerSize = 16;
            } else if (size == 0) {
                // 파일 끝까지
                size = end - (long) position;
            }

            if (size < headerSize || position + size > end) {
                return null;
            }

            if (boxType.equals(type)) {
                return new int[]{position + headerSize, (int) (position + size)};
            }
            position = (int) (position + size);
        }

        return null;
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.ISO_8859_1);
    }
}
