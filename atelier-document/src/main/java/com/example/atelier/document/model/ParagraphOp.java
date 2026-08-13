package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParagraphOp {
    private DiffOpType type;
    private Integer oldIndex;
    private Integer newIndex;
    private Integer movedTo;
    private String oldText;
    private String newText;
    private String blockType;
}
