package com.example.atelier.dimension.service;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionField;
import com.example.atelier.domain.dimension.DimensionType;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.TimeValueGenerateRequest;
import com.example.atelier.domain.dimension.TimeValueGenerateResult;
import com.example.atelier.domain.dimension.DimensionValueSource;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.DimensionEntity;
import com.example.atelier.infra.persistence.entity.DimensionFieldEntity;
import com.example.atelier.infra.persistence.entity.DimensionValueEntity;
import com.example.atelier.infra.persistence.entity.MetaTableEntity;
import com.example.atelier.infra.persistence.jpa.DimensionFieldJpaRepository;
import com.example.atelier.infra.persistence.jpa.DimensionJpaRepository;
import com.example.atelier.infra.persistence.jpa.DimensionValueJpaRepository;
import com.example.atelier.infra.persistence.jpa.MetaTableJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DimensionServiceImpl implements DimensionService {

    private final DimensionJpaRepository dimensionRepository;
    private final DimensionFieldJpaRepository fieldRepository;
    private final DimensionValueJpaRepository valueRepository;
    private final MetaTableJpaRepository metaTableRepository;
    private final TableDimensionValueLoader tableDimensionValueLoader;

    public DimensionServiceImpl(DimensionJpaRepository dimensionRepository,
                                DimensionFieldJpaRepository fieldRepository,
                                DimensionValueJpaRepository valueRepository,
                                MetaTableJpaRepository metaTableRepository,
                                TableDimensionValueLoader tableDimensionValueLoader) {
        this.dimensionRepository = dimensionRepository;
        this.fieldRepository = fieldRepository;
        this.valueRepository = valueRepository;
        this.metaTableRepository = metaTableRepository;
        this.tableDimensionValueLoader = tableDimensionValueLoader;
    }

    @Override
    public List<Dimension> listDimensions() {
        return dimensionRepository.findAll().stream().map(this::toDimension).collect(Collectors.toList());
    }

    @Override
    public Optional<Dimension> getDimension(String id) {
        return dimensionRepository.findById(id).map(this::toDimensionWithFields);
    }

    @Override
    public Optional<Dimension> getByCode(String code) {
        return dimensionRepository.findByDsCode(code).map(this::toDimensionWithFields);
    }

    @Override
    @Transactional
    public Dimension saveDimension(Dimension dimension) {
        if (dimension.getCode() == null || dimension.getCode().trim().isEmpty()) {
            throw new AtelierException("维度编码不能为空");
        }
        DimensionValueSource valueSource = dimension.getValueSource() != null
                ? dimension.getValueSource()
                : DimensionValueSource.MANUAL;
        if (valueSource == DimensionValueSource.TABLE) {
            validateTableDimension(dimension);
        }

        dimensionRepository.findByDsCode(dimension.getCode()).ifPresent(existing -> {
            if (dimension.getId() == null || !existing.getPkDimension().equals(dimension.getId())) {
                throw new AtelierException("维度编码已存在: " + dimension.getCode());
            }
        });
        DimensionEntity entity = dimension.getId() != null
                ? dimensionRepository.findById(dimension.getId()).orElse(newEntity(dimension))
                : newEntity(dimension);
        entity.setCatalogCode(dimension.getCatalogCode());
        entity.setDsCode(dimension.getCode());
        entity.setDsName(dimension.getName());
        entity.setDsType(dimension.getType() != null ? dimension.getType().name() : DimensionType.LIST.name());
        entity.setPkDatasource(dimension.getDatasourceId());
        entity.setPkMetaTable(dimension.getMetaTableId());
        entity.setValueSource(valueSource.name());
        entity.setComments(dimension.getComments());
        entity.setModifyTime(LocalDateTime.now());
        DimensionEntity saved = dimensionRepository.save(entity);

        if (dimension.getFields() != null) {
            fieldRepository.deleteByPkDimension(saved.getPkDimension());
            int sort = 1;
            for (DimensionField field : dimension.getFields()) {
                DimensionFieldEntity fieldEntity = DimensionFieldEntity.builder()
                        .pkDimField(UUID.randomUUID().toString())
                        .pkDimension(saved.getPkDimension())
                        .fieldCode(field.getFieldCode())
                        .fieldName(field.getFieldName())
                        .fieldType(field.getFieldType())
                        .codeField(boolToInt(field.getCodeField()))
                        .nameField(boolToInt(field.getNameField()))
                        .parentField(boolToInt(field.getParentField()))
                        .sortNo(field.getSort() != null ? field.getSort() : sort++)
                        .build();
                fieldRepository.save(fieldEntity);
            }
        }
        return toDimensionWithFields(saved);
    }

    @Override
    @Transactional
    public void deleteDimension(String id) {
        fieldRepository.deleteByPkDimension(id);
        valueRepository.deleteByPkDimension(id);
        dimensionRepository.deleteById(id);
    }

    @Override
    public List<DimensionValue> listValues(String dimensionId) {
        DimensionEntity entity = dimensionRepository.findById(dimensionId)
                .orElseThrow(() -> new AtelierException("维度不存在: " + dimensionId));
        if (isTableValueSource(entity)) {
            return loadValuesFromTable(entity);
        }
        return valueRepository.findByPkDimensionOrderBySortNoAsc(dimensionId).stream()
                .map(this::toValue)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DimensionValue saveValue(DimensionValue value) {
        DimensionEntity dimension = dimensionRepository.findById(value.getDimensionId())
                .orElseThrow(() -> new AtelierException("维度不存在: " + value.getDimensionId()));
        if (isTableValueSource(dimension)) {
            throw new AtelierException("表数据源维度的值来自物理表，不支持手动维护");
        }
        DimensionValueEntity entity = value.getId() != null
                ? valueRepository.findById(value.getId()).orElse(newValueEntity(value))
                : newValueEntity(value);
        entity.setPkDimension(value.getDimensionId());
        entity.setCode(value.getCode());
        entity.setName(value.getName());
        entity.setParentCode(value.getParentCode());
        entity.setSortNo(value.getSort());
        return toValue(valueRepository.save(entity));
    }

    @Override
    @Transactional
    public TimeValueGenerateResult generateTimeValues(String dimensionId, TimeValueGenerateRequest request) {
        DimensionEntity dimension = dimensionRepository.findById(dimensionId)
                .orElseThrow(() -> new AtelierException("维度不存在: " + dimensionId));
        if (!DimensionType.TIME_DIM.name().equals(dimension.getDsType())) {
            throw new AtelierException("仅时间维度支持批量生成");
        }
        if (isTableValueSource(dimension)) {
            throw new AtelierException("表数据源维度不支持批量生成，请从物理表读取");
        }
        List<DimensionValue> generated = TimeValueGenerator.generate(request);
        Set<String> existingCodes = valueRepository.findByPkDimensionOrderBySortNoAsc(dimensionId).stream()
                .map(DimensionValueEntity::getCode)
                .collect(Collectors.toCollection(HashSet::new));
        int maxSort = valueRepository.findByPkDimensionOrderBySortNoAsc(dimensionId).stream()
                .map(DimensionValueEntity::getSortNo)
                .filter(sort -> sort != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        List<DimensionValue> saved = new ArrayList<>();
        int skipped = 0;
        for (DimensionValue value : generated) {
            if (request.isSkipExisting() && existingCodes.contains(value.getCode())) {
                skipped++;
                continue;
            }
            if (existingCodes.contains(value.getCode())) {
                throw new AtelierException("维度值编码已存在: " + value.getCode());
            }
            value.setDimensionId(dimensionId);
            value.setSort(maxSort + saved.size() + 1);
            saved.add(saveValue(value));
            existingCodes.add(value.getCode());
        }
        return TimeValueGenerateResult.builder()
                .generated(saved.size())
                .skipped(skipped)
                .values(saved)
                .build();
    }

    @Override
    @Transactional
    public void deleteValue(String valueId) {
        DimensionValueEntity valueEntity = valueRepository.findById(valueId)
                .orElseThrow(() -> new AtelierException("维度值不存在: " + valueId));
        DimensionEntity dimension = dimensionRepository.findById(valueEntity.getPkDimension())
                .orElseThrow(() -> new AtelierException("维度不存在"));
        if (isTableValueSource(dimension)) {
            throw new AtelierException("表数据源维度的值来自物理表，不支持手动删除");
        }
        valueRepository.deleteById(valueId);
    }

    private void validateTableDimension(Dimension dimension) {
        if (dimension.getMetaTableId() == null || dimension.getMetaTableId().trim().isEmpty()) {
            throw new AtelierException("表数据源维度必须关联元数据表");
        }
        if (dimension.getFields() == null || dimension.getFields().isEmpty()) {
            throw new AtelierException("表数据源维度必须配置字段映射");
        }
        boolean hasCode = dimension.getFields().stream().anyMatch(f -> Boolean.TRUE.equals(f.getCodeField()));
        boolean hasName = dimension.getFields().stream().anyMatch(f -> Boolean.TRUE.equals(f.getNameField()));
        if (!hasCode || !hasName) {
            throw new AtelierException("表数据源维度必须指定编码列与名称列映射");
        }
    }

    private List<DimensionValue> loadValuesFromTable(DimensionEntity entity) {
        MetaTableEntity metaTable = metaTableRepository.findById(entity.getPkMetaTable())
                .orElseThrow(() -> new AtelierException("关联元数据表不存在: " + entity.getPkMetaTable()));
        List<DimensionField> fields = fieldRepository.findByPkDimensionOrderBySortNoAsc(entity.getPkDimension())
                .stream()
                .map(this::toField)
                .collect(Collectors.toList());
        return tableDimensionValueLoader.load(entity.getPkDimension(), metaTable, fields);
    }

    private boolean isTableValueSource(DimensionEntity entity) {
        return DimensionValueSource.TABLE.name().equals(entity.getValueSource());
    }

    private DimensionEntity newEntity(Dimension dimension) {
        return DimensionEntity.builder()
                .pkDimension(dimension.getId() != null ? dimension.getId() : UUID.randomUUID().toString())
                .valueSource(DimensionValueSource.MANUAL.name())
                .createTime(LocalDateTime.now())
                .build();
    }

    private DimensionValueEntity newValueEntity(DimensionValue value) {
        return DimensionValueEntity.builder()
                .pkDimValue(value.getId() != null ? value.getId() : UUID.randomUUID().toString())
                .build();
    }

    private Dimension toDimension(DimensionEntity entity) {
        return Dimension.builder()
                .id(entity.getPkDimension())
                .catalogCode(entity.getCatalogCode())
                .code(entity.getDsCode())
                .name(entity.getDsName())
                .type(entity.getDsType() != null ? DimensionType.valueOf(entity.getDsType()) : DimensionType.LIST)
                .datasourceId(entity.getPkDatasource())
                .metaTableId(entity.getPkMetaTable())
                .valueSource(parseValueSource(entity.getValueSource()))
                .comments(entity.getComments())
                .build();
    }

    private Dimension toDimensionWithFields(DimensionEntity entity) {
        Dimension dimension = toDimension(entity);
        dimension.setFields(fieldRepository.findByPkDimensionOrderBySortNoAsc(entity.getPkDimension()).stream()
                .map(this::toField)
                .collect(Collectors.toList()));
        return dimension;
    }

    private DimensionField toField(DimensionFieldEntity entity) {
        return DimensionField.builder()
                .id(entity.getPkDimField())
                .dimensionId(entity.getPkDimension())
                .fieldCode(entity.getFieldCode())
                .fieldName(entity.getFieldName())
                .fieldType(entity.getFieldType())
                .codeField(intToBool(entity.getCodeField()))
                .nameField(intToBool(entity.getNameField()))
                .parentField(intToBool(entity.getParentField()))
                .sort(entity.getSortNo())
                .build();
    }

    private DimensionValue toValue(DimensionValueEntity entity) {
        return DimensionValue.builder()
                .id(entity.getPkDimValue())
                .dimensionId(entity.getPkDimension())
                .code(entity.getCode())
                .name(entity.getName())
                .parentCode(entity.getParentCode())
                .sort(entity.getSortNo())
                .build();
    }

    private DimensionValueSource parseValueSource(String valueSource) {
        if (valueSource == null || valueSource.trim().isEmpty()) {
            return DimensionValueSource.MANUAL;
        }
        return DimensionValueSource.valueOf(valueSource);
    }

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    private Boolean intToBool(Integer value) {
        return value != null && value == 1;
    }
}
