package com.example.atelier.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class SemanticLlmProfilesSaveRequest {

    private String activeProfileId;

    private List<SemanticLlmProfileRequest> profiles;
}
