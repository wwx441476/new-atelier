package com.example.atelier.document.preview;

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
public class PreviewRun {
    private String text;
    @Builder.Default
    private List<PreviewInlineMark> marks = new ArrayList<>();
}
