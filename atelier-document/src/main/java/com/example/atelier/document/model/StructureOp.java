package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructureOp {
    private DiffOpType type;
    private String path;
    private String blockType;
    private String oldText;
    private String newText;
    private String detail;
}
