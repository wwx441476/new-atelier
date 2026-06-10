package com.example.atelier.domain.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlExecuteResult {

    private String sql;

    /** SELECT / INSERT / UPDATE / DELETE / CREATE TABLE / ALTER TABLE / DROP TABLE */
    private String statementType;

    private int affectedRows;

    private String message;
}
