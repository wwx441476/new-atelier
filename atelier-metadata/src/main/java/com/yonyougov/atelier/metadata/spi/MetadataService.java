package com.yonyougov.atelier.metadata.spi;

import com.yonyougov.atelier.domain.metadata.MetaTable;
import com.yonyougov.atelier.domain.metadata.MetaTableField;
import com.yonyougov.atelier.domain.query.QueryResult;

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

    /** 分页预览元数据表对应的物理表数据 */
    QueryResult previewTableData(String tableId, int pageIndex, int pageSize);
}
