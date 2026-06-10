package com.example.atelier.metadata.spi;

import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableDdlResult;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metadata.MetaTableImportRequest;
import com.example.atelier.domain.metadata.MetaTableImportResult;
import com.example.atelier.domain.query.QueryResult;

import java.util.List;
import java.util.Optional;

/**
 * 元数据管理 SPI — 表与字段 CRUD。
 */
public interface MetadataService {

    List<MetaTable> listTables();

    List<MetaTable> listTablesByDatasource(String datasourceId);

    Optional<MetaTable> getTable(String id);

    MetaTable saveTable(MetaTable table);

    void deleteTable(String id);

    List<MetaTableField> listFields(String tableId);

    MetaTableField saveField(MetaTableField field);

    void deleteField(String fieldId);

    /** 从 JDBC 元数据发现表（简化桩） */
    List<MetaTable> discoverTables(String datasourceId);

    /** 从物理库导入选定表及其字段为元数据 */
    MetaTableImportResult importTablesFromDatabase(MetaTableImportRequest request);

    /** 分页预览元数据表对应的物理表数据 */
    QueryResult previewTableData(String tableId, int pageIndex, int pageSize);

    /** 根据元数据字段生成建表 DDL */
    MetaTableDdlResult buildCreateTableDdl(String tableId);

    /** 在目标数据源执行建表 DDL */
    void executeCreateTable(String tableId);

    /** 在目标数据源执行增量加字段 DDL */
    void executeSyncTable(String tableId);
}
