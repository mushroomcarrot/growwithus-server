package community.likelion.growwithusserver.common.ai;

import java.util.Optional;

// 인물 사진으로 캐릭터 아바타 그림을 만든다. 만들지 못하면 비어 있고, 호출한 쪽이 기본 아바타로 대체한다.
public interface AvatarImageClient {
    /**
     * @param photo jpg 또는 png 사진
     * @return 정사각형 PNG. 제공자가 없거나 실패하면 비어 있다
     */
    Optional<byte[]> generate(byte[] photo);
}
