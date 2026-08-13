package com.example.atelier.document.preview;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.TableData;
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
public class PreviewBlock {
    private String id;
    private PreviewBlockType type;
    private int level;
    /** 纯文本（runs 拼接），便于检索与兼容 */
    private String text;
    @Builder.Default
    private List<PreviewRun> runs = new ArrayList<>();
    private PreviewBlockAnchor anchor;
    private TableData table;
    /** data URL，如 data:image/png;base64,... */
    private String imageDataUrl;
    private BlockMeta meta;
}
