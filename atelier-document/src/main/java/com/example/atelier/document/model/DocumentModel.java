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
public class DocumentModel {
    private String fileName;
    private String mimeType;
    @Builder.Default
    private List<DocumentBlock> blocks = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    private boolean ocrUsed;

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        appendBlocks(blocks, sb);
        return sb.toString();
    }

    private void appendBlocks(List<DocumentBlock> list, StringBuilder sb) {
        if (list == null) {
            return;
        }
        for (DocumentBlock block : list) {
            if (block.getType() == BlockType.TABLE && block.getTable() != null) {
                if (block.getTable().getSheetName() != null) {
                    sb.append("[Sheet: ").append(block.getTable().getSheetName()).append("]\n");
                }
                for (List<String> row : block.getTable().getRows()) {
                    sb.append(String.join("\t", row)).append('\n');
                }
            } else if (block.getText() != null && !block.getText().isEmpty()) {
                sb.append(block.getText()).append('\n');
            }
            appendBlocks(block.getChildren(), sb);
        }
    }
}
