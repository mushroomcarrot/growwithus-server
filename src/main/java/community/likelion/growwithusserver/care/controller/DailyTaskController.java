package community.likelion.growwithusserver.care.controller;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.care.service.*;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequiredArgsConstructor
public class DailyTaskController {
    private final TaskGenerationService generation;
    @PostMapping("/internal/ai/daily-tasks")
    public GlobalResponse<TaskGenerationStore.Generated> generate(@jakarta.validation.Valid @RequestBody(required = false) TaskDtos.BatchRequest request) {
        return GlobalResponse.ok(generation.generateAll(request == null || request.getDate() == null ? LocalDate.now() : request.getDate(),
                request == null || request.getGardenIds() == null ? java.util.List.of() : request.getGardenIds()));
    }
}
