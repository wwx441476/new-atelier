package com.example.atelier.document.preview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewBlockAnchor {
    private Integer page;
    private String textHash;
    /** 同页同 textHash 的出现序号，从 1 起 */
    private int occurrence;
    /** 相对该页归一化文本的起始偏移（含） */
    private Integer sourceStart;
    /** 相对该页归一化文本的结束偏移（不含） */
    private Integer sourceEnd;
}
