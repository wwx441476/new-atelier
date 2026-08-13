package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockMeta {
    private Integer page;
    private String sheet;
    private Integer slideIndex;
    private Double ocrConfidence;
    private String styleHints;
}
