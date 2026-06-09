package com.example.atelier.domain.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbColumnInfo {

    private String name;

    private String typeName;

    private Integer columnSize;

    private Integer decimalDigits;

    private Boolean nullable;

    private String remarks;

    private Integer ordinalPosition;
}
