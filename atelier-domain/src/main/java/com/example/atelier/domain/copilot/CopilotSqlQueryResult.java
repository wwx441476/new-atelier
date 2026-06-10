package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotSqlQueryResult {

    private String datasourceId;

    private int pageIndex;

    private int pageSize;

    private long total;

    private List<Map<String, Object>> rows;

    private Map<String, String> headers;

    private String sql;
}
