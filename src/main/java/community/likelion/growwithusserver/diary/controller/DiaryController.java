package community.likelion.growwithusserver.diary.controller;
import community.likelion.growwithusserver.common.pagination.*;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class DiaryController {
    private final DiaryService diary;
    @GetMapping("/gardens/{gardenId}/diary")
    public GlobalResponse<CursorPageResponse<DiaryService.Item>> list(@PathVariable String gardenId,
            @RequestParam(defaultValue = "ALL") DiaryService.Filter filter, @Valid @ModelAttribute CursorPageRequest page) {
        return diary.list(SecurityUtil.getUsername(), gardenId, filter, page);
    }
}
