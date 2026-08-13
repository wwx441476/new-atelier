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
public class DocumentBlock {
    private String id;
    private BlockType type;
    private int level;
    private String text;
    private TableData table;
    /** data URL，如 data:image/png;base64,... */
    private String imageDataUrl;
    private BlockMeta meta;
    @Builder.Default
    private List<DocumentBlock> children = new ArrayList<>();
}
