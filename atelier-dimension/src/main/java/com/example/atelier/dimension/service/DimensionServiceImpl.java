package com.example.atelier.dimension.service;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionField;
import com.example.atelier.domain.dimension.DimensionType;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.DimensionEntity;
import com.example.atelier.infra.persistence.entity.DimensionFieldEntity;
import com.example.atelier.infra.persistence.entity.DimensionValueEntity;
import com.example.atelier.infra.persistence.jpa.DimensionFieldJpaRepository;
import com.example.atelier.infra.persistence.jpa.DimensionJpaRepository;
import com.example.atelier.infra.persistence.jpa.DimensionValueJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DimensionServiceImpl implements DimensionService {

    private final DimensionJpaRepository dimensionRepository;
    private final DimensionFieldJpaRepository fieldRepository;
    private final DimensionValueJpaRepository valueRepository;

    public DimensionServiceImpl(DimensionJpaRepository dimensionRepository,
                                DimensionFieldJpaRepository fieldRepository,
                                DimensionValueJpaRepository valueRepository) {
        this.dimensionRepository = dimensionRepository;
        this.fieldRepository = fieldRepository;
        this.valueRepository = valueRepository;
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
        return valueRepository.findByPkDimensionOrderBySortNoAsc(dimensionId).stream()
                .map(this::toValue)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DimensionValue saveValue(DimensionValue value) {
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
    public void deleteValue(String valueId) {
        valueRepository.deleteById(valueId);
    }

    private DimensionEntity newEntity(Dimension dimension) {
        return DimensionEntity.builder()
                .pkDimension(dimension.getId() != null ? dimension.getId() : UUID.randomUUID().toString())
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

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    private Boolean intToBool(Integer value) {
        return value != null && value == 1;
    }
}
