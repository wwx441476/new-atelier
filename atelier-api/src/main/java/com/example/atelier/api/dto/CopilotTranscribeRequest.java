package com.example.atelier.api.dto;

import lombok.Data;

@Data
public class CopilotTranscribeRequest {

    /** data:audio/webm;base64,... */
    private String audioDataUrl;

    private String llmProfileId;
}
