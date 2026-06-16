package com.example.atelier.api.copilot;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.copilot.CopilotActivePlan;
import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.infra.persistence.service.CopilotPlaybookService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/copilot/playbooks")
public class CopilotPlaybookController {

    private final CopilotPlaybookService playbookService;
    private final CopilotPlaybookMatcher playbookMatcher;

    public CopilotPlaybookController(CopilotPlaybookService playbookService,
                                     CopilotPlaybookMatcher playbookMatcher) {
        this.playbookService = playbookService;
        this.playbookMatcher = playbookMatcher;
    }

    @GetMapping
    public ApiResponse<List<CopilotPlaybook>> list() {
        return ApiResponse.ok(playbookService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<CopilotPlaybook> getById(@PathVariable String id) {
        return playbookService.getById(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("技能不存在: " + id));
    }

    @PostMapping
    public ApiResponse<CopilotPlaybook> save(@RequestBody CopilotPlaybook playbook) {
        return ApiResponse.ok(playbookService.save(playbook));
    }

    @PostMapping("/from-plan")
    public ApiResponse<CopilotPlaybook> saveFromPlan(@RequestBody SavePlaybookFromPlanRequest request) {
        if (request == null || request.getPlan() == null || request.getPlan().getSteps() == null) {
            return ApiResponse.fail("计划为空，无法保存技能");
        }
        CopilotPlaybook playbook = CopilotPlaybook.builder()
                .code(request.getCode() != null ? request.getCode()
                        : "playbook-" + Long.toString(System.currentTimeMillis(), 36))
                .name(request.getName() != null ? request.getName() : "未命名技能")
                .description(request.getDescription())
                .triggerKeywords(request.getTriggerKeywords())
                .enabled(true)
                .usageCount(0)
                .build();
        int order = 1;
        for (com.example.atelier.domain.copilot.CopilotPlanStep step : request.getPlan().getSteps()) {
            playbook.getSteps().add(com.example.atelier.domain.copilot.CopilotPlaybookStep.builder()
                    .title(step.getTitle())
                    .tool(step.getTool())
                    .description(step.getDescription())
                    .order(order++)
                    .build());
        }
        return ApiResponse.ok(playbookService.save(playbook));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<CopilotActivePlan> activate(@PathVariable String id) {
        return playbookService.getById(id)
                .map(playbook -> {
                    playbookService.incrementUsage(id);
                    return ApiResponse.ok(playbookMatcher.toActivePlan(playbook));
                })
                .orElseGet(() -> ApiResponse.fail("技能不存在: " + id));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        playbookService.deleteByCode(code);
        return ApiResponse.ok(null);
    }

    public static class SavePlaybookFromPlanRequest {
        private String code;
        private String name;
        private String description;
        private List<String> triggerKeywords;
        private CopilotActivePlan plan;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getTriggerKeywords() {
            return triggerKeywords;
        }

        public void setTriggerKeywords(List<String> triggerKeywords) {
            this.triggerKeywords = triggerKeywords;
        }

        public CopilotActivePlan getPlan() {
            return plan;
        }

        public void setPlan(CopilotActivePlan plan) {
            this.plan = plan;
        }
    }
}
