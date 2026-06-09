package com.example.atelier.domain.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbTableInfo {

    private String schema;

    private String name;

    /** TABLE / VIEW */
    private String type;

    private String remarks;
}
