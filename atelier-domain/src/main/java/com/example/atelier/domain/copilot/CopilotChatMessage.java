package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotChatMessage {

    /** user | assistant */
    private String role;

    private String content;

    /** 截图 data URL，如 data:image/png;base64,... */
    private java.util.List<String> images;
}
