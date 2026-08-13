package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareJob {
    private String id;
    private CompareJobStatus status;
    private String progress;
    private int progressPercent;
    private String fileNameA;
    private String fileNameB;
    private CompareOptions options;
    private CompareResult result;
    private String error;
    private long createdAt;
    private long updatedAt;
}
