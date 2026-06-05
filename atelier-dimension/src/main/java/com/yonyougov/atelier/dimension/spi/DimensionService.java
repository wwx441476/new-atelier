package com.yonyougov.atelier.dimension.spi;

import com.yonyougov.atelier.domain.dimension.Dimension;
import com.yonyougov.atelier.domain.dimension.DimensionField;
import com.yonyougov.atelier.domain.dimension.DimensionValue;

import java.util.List;
import java.util.Optional;

/**
 * 维度管理 SPI。
 */
public interface DimensionService {

    List<Dimension> listDimensions();

    Optional<Dimension> getDimension(String id);

    Optional<Dimension> getByCode(String code);

    Dimension saveDimension(Dimension dimension);

    void deleteDimension(String id);

    List<DimensionValue> listValues(String dimensionId);

    DimensionValue saveValue(DimensionValue value);

    void deleteValue(String valueId);
}
