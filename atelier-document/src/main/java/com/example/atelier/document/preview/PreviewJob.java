package com.example.atelier.document.preview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewJob {
    private String id;
    private PreviewJobStatus status;
    private String progress;
    private int progressPercent;
    private String fileName;
    private PreviewDocument result;
    private String error;
    private long createdAt;
    private long updatedAt;
}
