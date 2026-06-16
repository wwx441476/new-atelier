package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.infra.persistence.service.DashboardScreenService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可视化大屏 CRUD API — /api/v2/dashboards。
 */
@RestController
@RequestMapping("/api/v2/dashboards")
public class DashboardController {

    private final DashboardScreenService dashboardScreenService;

    public DashboardController(DashboardScreenService dashboardScreenService) {
        this.dashboardScreenService = dashboardScreenService;
    }

    @GetMapping
    public ApiResponse<List<DashboardScreen>> list() {
        return ApiResponse.ok(dashboardScreenService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DashboardScreen> getById(@PathVariable String id) {
        return dashboardScreenService.getById(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("大屏不存在: " + id));
    }

    @GetMapping("/by-code/{code}")
    public ApiResponse<DashboardScreen> getByCode(@PathVariable String code) {
        return dashboardScreenService.getByCode(code)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("大屏不存在: " + code));
    }

    @PostMapping
    public ApiResponse<DashboardScreen> save(@RequestBody DashboardScreen screen) {
        return ApiResponse.ok(dashboardScreenService.save(screen));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        dashboardScreenService.deleteByCode(code);
        return ApiResponse.ok(null);
    }
}
