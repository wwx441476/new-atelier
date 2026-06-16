package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可视化大屏定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardScreen {

    private String id;

    private String code;

    private String name;

    private String description;

    private Boolean enabled;

    @Builder.Default
    private DashboardLayoutConfig layout = DashboardLayoutConfig.builder().build();

    @Builder.Default
    private List<DashboardWidget> widgets = new ArrayList<>();
}
