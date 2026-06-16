package com.example.atelier.api.dashboard;

import com.example.atelier.domain.dashboard.DashboardLayoutConfig;
import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.domain.dashboard.DashboardWidget;
import com.example.atelier.domain.dashboard.DashboardWidgetDataSource;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.infra.persistence.service.DashboardScreenService;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DashboardScreenNormalizer {

    private final DashboardScreenService dashboardScreenService;
    private final WarningRuleService warningRuleService;

    public DashboardScreenNormalizer(DashboardScreenService dashboardScreenService,
                                     WarningRuleService warningRuleService) {
        this.dashboardScreenService = dashboardScreenService;
        this.warningRuleService = warningRuleService;
    }

    public DashboardScreen normalize(DashboardScreen screen) {
        if (screen == null) {
            throw new IllegalArgumentException("大屏定义为空");
        }
        if (screen.getCode() == null || screen.getCode().trim().isEmpty()) {
            screen.setCode("screen-" + Long.toString(System.currentTimeMillis(), 36));
        }
        screen.setCode(ensureUniqueCode(screen.getCode().trim().toLowerCase(Locale.ROOT)));
        if (screen.getName() == null || screen.getName().trim().isEmpty()) {
            screen.setName("AI 生成大屏");
        }
        if (screen.getEnabled() == null) {
            screen.setEnabled(true);
        }
        if (screen.getLayout() == null) {
            screen.setLayout(DashboardLayoutConfig.builder().build());
        }
        DashboardLayoutConfig layout = screen.getLayout();
        if (layout.getWidth() == null) {
            layout.setWidth(1920);
        }
        if (layout.getHeight() == null) {
            layout.setHeight(1080);
        }
        if (layout.getGridCols() == null) {
            layout.setGridCols(24);
        }
        if (layout.getRowHeight() == null) {
            layout.setRowHeight(30);
        }
        if (layout.getTheme() == null || layout.getTheme().trim().isEmpty()) {
            layout.setTheme("tech-blue");
        }
        Map<String, WarningRule> rulesById = new HashMap<>();
        Map<String, WarningRule> rulesByCode = new HashMap<>();
        for (WarningRule rule : warningRuleService.listRules()) {
            if (rule.getId() != null) {
                rulesById.put(rule.getId(), rule);
            }
            if (rule.getCode() != null) {
                rulesByCode.put(rule.getCode(), rule);
            }
        }
        List<DashboardWidget> widgets = screen.getWidgets();
        if (widgets != null) {
            for (int i = 0; i < widgets.size(); i++) {
                DashboardWidget widget = widgets.get(i);
                if (widget.getId() == null || widget.getId().trim().isEmpty()) {
                    widget.setId("w-" + (i + 1));
                }
                if (widget.getW() == null || widget.getW() <= 0) {
                    widget.setW(6);
                }
                if (widget.getH() == null || widget.getH() <= 0) {
                    widget.setH(4);
                }
                if (widget.getX() == null) {
                    widget.setX(0);
                }
                if (widget.getY() == null) {
                    widget.setY(0);
                }
                normalizeWidgetDataSource(widget);
                resolveWarningBinding(widget.getDataSource(), rulesById, rulesByCode);
            }
        }
        return screen;
    }

    private void normalizeWidgetDataSource(DashboardWidget widget) {
        if (widget == null || widget.getType() == null) {
            return;
        }
        String type = widget.getType().name();
        if ("TITLE".equals(type)) {
            return;
        }
        DashboardWidgetDataSource dataSource = widget.getDataSource();
        if (dataSource == null) {
            dataSource = new DashboardWidgetDataSource();
            widget.setDataSource(dataSource);
        }
        if (dataSource.getBindType() == null || dataSource.getBindType().trim().isEmpty()) {
            if (type.startsWith("METRIC_")) {
                dataSource.setBindType("METRIC");
            } else if (type.startsWith("WARNING_")) {
                dataSource.setBindType("WARNING");
            } else if (type.startsWith("SQL_")) {
                dataSource.setBindType("SQL");
            }
        }
        if (dataSource.getPageSize() == null) {
            dataSource.setPageSize(type.contains("TABLE") ? 10 : 20);
        }
        if (("METRIC_VALUE".equals(type) || "SQL_VALUE".equals(type))
                && (dataSource.getValueField() == null || dataSource.getValueField().trim().isEmpty())
                && dataSource.getMetricCodes() != null
                && !dataSource.getMetricCodes().isEmpty()) {
            dataSource.setValueField(dataSource.getMetricCodes().get(0));
        }
    }

    private void resolveWarningBinding(DashboardWidgetDataSource dataSource,
                                       Map<String, WarningRule> rulesById,
                                       Map<String, WarningRule> rulesByCode) {
        if (dataSource == null || !"WARNING".equalsIgnoreCase(dataSource.getBindType())) {
            return;
        }
        String ruleId = dataSource.getRuleId();
        if (ruleId != null && rulesById.containsKey(ruleId)) {
            return;
        }
        if (ruleId != null && rulesByCode.containsKey(ruleId)) {
            dataSource.setRuleId(rulesByCode.get(ruleId).getId());
            return;
        }
        for (WarningRule rule : rulesByCode.values()) {
            if (ruleId != null && ruleId.equals(rule.getName())) {
                dataSource.setRuleId(rule.getId());
                return;
            }
        }
    }

    private String ensureUniqueCode(String code) {
        String candidate = code;
        int suffix = 1;
        while (dashboardScreenService.getByCode(candidate).isPresent()) {
            candidate = code + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    public DashboardScreen save(DashboardScreen screen) {
        screen.setId(null);
        return dashboardScreenService.save(screen);
    }
}
