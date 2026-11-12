package community.likelion.growwithusserver.voice.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmRequestDto;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmResponseDto;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoResponseDto;
import community.likelion.growwithusserver.voice.service.VoiceMemoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/caretaker")
@RequiredArgsConstructor
public class VoiceMemoController {
    private final VoiceMemoService service;

    @PostMapping(value = "/tasks/{taskId}/voice-memos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "음성 메모 인식과 초안 저장")
    public GlobalResponse<VoiceMemoResponseDto> create(@PathVariable String taskId,
                                                      @RequestPart("audio") MultipartFile audio) {
        return service.create(SecurityUtil.getUsername(), taskId, audio);
    }

    @PostMapping("/voice-memos/{voiceMemoId}/confirm")
    @Operation(summary = "본인 음성 메모 확정")
    public GlobalResponse<VoiceMemoConfirmResponseDto> confirm(@PathVariable String voiceMemoId,
                                                             @Valid @RequestBody(required = false) VoiceMemoConfirmRequestDto request) {
        return service.confirm(SecurityUtil.getUsername(), voiceMemoId, request);
    }

    @PostMapping("/voice-memos/{voiceMemoId}/retry")
    @Operation(summary = "실패한 음성 메모 인식과 정리 재시도")
    public GlobalResponse<VoiceMemoResponseDto> retry(@PathVariable String voiceMemoId) {
        return service.retry(SecurityUtil.getUsername(), voiceMemoId);
    }
}
