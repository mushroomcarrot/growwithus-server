package community.likelion.growwithusserver.common.file.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 파일 형식, 크기, 길이 제한 위반
public class FileNotSupportedException extends GlobalException {
    public FileNotSupportedException() {
        super(ErrorCode.FILE_NOT_SUPPORTED);
    }
}
