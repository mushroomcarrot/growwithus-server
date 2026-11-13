package community.likelion.growwithusserver.care.controller;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.care.service.*;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/caretaker/tasks") @RequiredArgsConstructor
public class TaskController {
    private final TodayTaskService today;
    private final TaskPhotoService photos;
    private final TaskWriteService store;
    @GetMapping("/today")
    public GlobalResponse<TaskDtos.Today> today() { return today.today(SecurityUtil.getUsername()); }
    @PostMapping(value = "/{taskId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GlobalResponse<TaskDtos.Photo> photo(@PathVariable String taskId, @RequestPart("photo") MultipartFile photo) {
        return photos.upload(SecurityUtil.getUsername(), taskId, photo);
    }
    @PostMapping("/{taskId}/complete")
    public GlobalResponse<TaskDtos.Completed> complete(@PathVariable String taskId,
            @Valid @RequestBody(required = false) TaskDtos.CompleteRequest request) {
        return GlobalResponse.ok(store.complete(SecurityUtil.getUsername(), IdPrefix.TASK.parse(taskId), request));
    }
}
