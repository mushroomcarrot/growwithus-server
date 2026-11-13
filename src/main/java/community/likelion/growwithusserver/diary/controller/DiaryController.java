package community.likelion.growwithusserver.diary.controller;

import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.diary.data.dto.DiaryItemDto;
import community.likelion.growwithusserver.diary.data.enums.DiaryFilter;
import community.likelion.growwithusserver.diary.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DiaryController {
    private final DiaryService diaryService;

    @GetMapping("/gardens/{gardenId}/diary")
    @Operation(summary = "성장 일기 (ALL, PHOTO, STAGE 필터, cursor는 날짜)")
    public GlobalResponse<CursorPageResponse<DiaryItemDto>> getDiary(@PathVariable String gardenId,
                                                                     @RequestParam(defaultValue = "ALL") DiaryFilter filter,
                                                                     @Valid @ModelAttribute CursorPageRequest pageRequest) {
        return diaryService.getDiary(SecurityUtil.getUsername(), gardenId, filter, pageRequest);
    }
}
