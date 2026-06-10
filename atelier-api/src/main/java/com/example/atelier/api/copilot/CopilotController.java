package com.example.atelier.api.copilot;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.copilot.CopilotChatRequest;
import com.example.atelier.domain.copilot.CopilotChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据工场智能助手 — /api/v2/copilot。
 */
@RestController
@RequestMapping("/api/v2/copilot")
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @PostMapping("/chat")
    public ApiResponse<CopilotChatResponse> chat(@RequestBody CopilotChatRequest request) {
        return ApiResponse.ok(copilotService.chat(request));
    }
}
