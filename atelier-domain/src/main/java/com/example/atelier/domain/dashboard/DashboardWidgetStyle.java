package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWidgetStyle {

    private Integer fontSize;

    private String color;

    /** left / center / right */
    private String textAlign;
}
