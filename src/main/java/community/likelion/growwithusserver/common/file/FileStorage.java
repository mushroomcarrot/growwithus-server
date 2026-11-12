package community.likelion.growwithusserver.common.file;

import java.io.InputStream;

// 파일 저장소 추상화. 현재 구현은 LocalFileStorage 하나이고, S3 등은 file.storage 값으로 구현체를 바꿔 끼운다.
public interface FileStorage {
    /**
     * @param directory   저장소 안의 하위 경로 (예: care-photos)
     * @param filename    저장할 파일 이름 (확장자 포함)
     * @param content     파일 내용
     * @param size        파일 크기 (byte)
     * @param contentType MIME 타입
     * @return 클라이언트가 접근할 수 있는 URL
     */
    String store(String directory, String filename, InputStream content, long size, String contentType);

    default byte[] readAudio(String storedUrl) {
        throw new community.likelion.growwithusserver.common.file.exception.FileUploadFailedException();
    }

    default void deleteAudio(String storedUrl) {
        throw new community.likelion.growwithusserver.common.file.exception.FileUploadFailedException();
    }
}
