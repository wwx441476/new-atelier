package com.example.atelier.domain.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbCreateTableRequest {

    private String schema;

    private String tableName;

    private List<DbCreateTableColumn> columns;

    private Boolean ifNotExists;
}
