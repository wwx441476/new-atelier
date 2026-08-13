package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextHunk {
    private DiffOpType type;
    private int oldStart;
    private int newStart;
    @Builder.Default
    private List<String> oldLines = new ArrayList<>();
    @Builder.Default
    private List<String> newLines = new ArrayList<>();
}
