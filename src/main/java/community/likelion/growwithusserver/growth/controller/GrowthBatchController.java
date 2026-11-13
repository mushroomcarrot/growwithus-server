package community.likelion.growwithusserver.growth.controller;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.growth.service.GrowthBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
public class GrowthBatchController {
    private final GrowthBatchService growth;
    @PostMapping("/internal/ai/simulate-growth")
    public GlobalResponse<GrowthBatchService.Result> simulate(@RequestBody(required = false) TaskDtos.BatchRequest request) {
        return GlobalResponse.ok(growth.run(request == null || request.getDate() == null ? LocalDate.now() : request.getDate(),
                request == null || request.getGardenIds() == null ? List.of() : request.getGardenIds()));
    }
}
