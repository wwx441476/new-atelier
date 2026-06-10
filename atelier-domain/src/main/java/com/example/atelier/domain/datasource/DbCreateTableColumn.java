package com.example.atelier.domain.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbCreateTableColumn {

    private String name;

    /** 如 VARCHAR(50)、DECIMAL(18,2)、INT */
    private String type;

    private Boolean nullable;

    private Boolean primaryKey;
}
