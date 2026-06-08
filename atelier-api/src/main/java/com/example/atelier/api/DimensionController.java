package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 维度管理 API — /api/v2/dimensions。
 */
@RestController
@RequestMapping("/api/v2/dimensions")
public class DimensionController {

    private final DimensionService dimensionService;

    public DimensionController(DimensionService dimensionService) {
        this.dimensionService = dimensionService;
    }

    @GetMapping
    public ApiResponse<List<Dimension>> list() {
        return ApiResponse.ok(dimensionService.listDimensions());
    }

    @GetMapping("/{id}")
    public ApiResponse<Dimension> get(@PathVariable String id) {
        return dimensionService.getDimension(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("维度不存在: " + id));
    }

    @PostMapping
    public ApiResponse<Dimension> save(@RequestBody Dimension dimension) {
        return ApiResponse.ok(dimensionService.saveDimension(dimension));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        dimensionService.deleteDimension(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/values")
    public ApiResponse<List<DimensionValue>> listValues(@PathVariable String id) {
        return ApiResponse.ok(dimensionService.listValues(id));
    }

    @PostMapping("/{id}/values")
    public ApiResponse<DimensionValue> saveValue(@PathVariable String id, @RequestBody DimensionValue value) {
        value.setDimensionId(id);
        return ApiResponse.ok(dimensionService.saveValue(value));
    }

    @DeleteMapping("/values/{valueId}")
    public ApiResponse<Void> deleteValue(@PathVariable String valueId) {
        dimensionService.deleteValue(valueId);
        return ApiResponse.ok(null);
    }
}
