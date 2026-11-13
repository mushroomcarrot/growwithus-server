package community.likelion.growwithusserver.diary.controller;
import community.likelion.growwithusserver.common.pagination.*;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.diary.service.LocalSnapshotDiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("local-demo")
@RestController @RequiredArgsConstructor
public class LocalSnapshotDiaryController {
    private final LocalSnapshotDiaryService diary;
    @GetMapping("/gardens/{gardenId}/diary")
    public GlobalResponse<CursorPageResponse<LocalSnapshotDiaryService.Item>> list(@PathVariable String gardenId,
            @RequestParam(defaultValue = "ALL") LocalSnapshotDiaryService.Filter filter, @Valid @ModelAttribute CursorPageRequest page) {
        return diary.list(SecurityUtil.getUsername(), gardenId, filter, page);
    }
}
