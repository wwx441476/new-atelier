package com.example.atelier.domain.settings;

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
public class SemanticLlmProfilesSettings {

    private String activeProfileId;

    @Builder.Default
    private List<SemanticLlmProfile> profiles = new ArrayList<>();
}
