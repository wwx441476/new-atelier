package com.example.atelier.metadata.ddl;

import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.infra.datasource.DbType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class TableDdlBuilderTest {

    @Test
    public void shouldBuildAddColumnStatementForH2() {
        MetaTableField field = MetaTableField.builder()
                .fieldCode("remark")
                .fieldName("备注")
                .fieldType("VARCHAR")
                .fieldLength(255)
                .build();

        String sql = TableDdlBuilder.buildAddColumnStatements(
                DbType.H2, "PUBLIC", "orders", Collections.singletonList(field)).get(0);

        Assert.assertEquals("ALTER TABLE PUBLIC.orders ADD COLUMN remark VARCHAR(255)", sql);
    }

    @Test
    public void shouldNotAddNotNullWhenAlteringExistingTable() {
        MetaTableField field = MetaTableField.builder()
                .fieldCode("remark")
                .fieldName("备注")
                .fieldType("VARCHAR")
                .fieldLength(255)
                .nullable(false)
                .build();

        String sql = TableDdlBuilder.buildAddColumnStatements(
                DbType.H2, "PUBLIC", "orders", Collections.singletonList(field)).get(0);

        Assert.assertEquals("ALTER TABLE PUBLIC.orders ADD COLUMN remark VARCHAR(255)", sql);
        Assert.assertFalse(sql.contains("NOT NULL"));
    }
}
