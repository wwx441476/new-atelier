package com.example.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTableImportResult {

    private List<MetaTable> imported;

    /** 已存在而跳过的表名 */
    private List<String> skipped;

    private int importedCount;

    private int skippedCount;
}
