package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.common.ai.VisionClient;
import community.likelion.growwithusserver.common.file.FileUploadService;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class TaskPhotoService {
    private final TaskAccess access;
    private final FileUploadService upload;
    private final VisionClient vision;
    private final TaskWriteService store;
    public GlobalResponse<TaskDtos.Photo> upload(String username, String publicTaskId, MultipartFile photo) {
        Long id = IdPrefix.TASK.parse(publicTaskId);
        var task = access.open(username, id, false);
        String url = upload.uploadImage(photo, "care-photos");
        VisionClient.Check check = null;
        try { check = vision.check(photo.getBytes(), task.getTitle() + ": " + task.getDescription()).orElse(null); }
        catch (java.io.IOException | RuntimeException ignored) {
            // AI 확인이 실패해도 사진은 저장한다.
        }
        return GlobalResponse.ok(store.savePhoto(username, id, url, check));
    }
}
