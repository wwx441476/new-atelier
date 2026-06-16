package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWidget {

    private String id;

    private DashboardWidgetType type;

    private String title;

    /** 网格布局 x（列） */
    private Integer x;

    /** 网格布局 y（行） */
    private Integer y;

    /** 宽度（列数） */
    private Integer w;

    /** 高度（行数） */
    private Integer h;

    /** 静态文本（TITLE 组件） */
    private String content;

    private DashboardWidgetStyle style;

    private DashboardWidgetDataSource dataSource;
}
