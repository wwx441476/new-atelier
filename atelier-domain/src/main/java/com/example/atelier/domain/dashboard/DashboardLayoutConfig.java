package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutConfig {

    @Builder.Default
    private Integer width = 1920;

    @Builder.Default
    private Integer height = 1080;

    private String backgroundColor;

    private String backgroundImage;

    @Builder.Default
    private Integer gridCols = 24;

    @Builder.Default
    private Integer rowHeight = 30;

    /** 主题：tech-blue / aurora / light / emerald */
    private String theme;
}
