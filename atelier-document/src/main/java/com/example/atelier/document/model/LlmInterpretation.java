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
public class LlmInterpretation {
    private boolean available;
    private String summary;
    @Builder.Default
    private List<String> impactPoints = new ArrayList<>();
    @Builder.Default
    private List<String> reviewChecklist = new ArrayList<>();
    private String error;
}
