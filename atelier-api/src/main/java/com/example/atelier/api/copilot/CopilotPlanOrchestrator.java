package com.example.atelier.api.copilot;

import com.example.atelier.domain.copilot.CopilotActionResult;
import com.example.atelier.domain.copilot.CopilotActivePlan;
import com.example.atelier.domain.copilot.CopilotPlanStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CopilotPlanOrchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CopilotActivePlan mergePlan(List<CopilotPlanStep> parsedPlan,
                                       CopilotActivePlan existing,
                                       List<CopilotActionResult> actionResults) {
        CopilotActivePlan plan = existing != null ? existing : CopilotActivePlan.builder().build();
        if ((plan.getSteps() == null || plan.getSteps().isEmpty()) && parsedPlan != null && !parsedPlan.isEmpty()) {
            plan.setSteps(parsedPlan);
            plan.setPlanId(plan.getPlanId() != null ? plan.getPlanId() : UUID.randomUUID().toString());
            plan.setCurrentStepIndex(0);
        }
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return null;
        }
        updateStepStatuses(plan, actionResults);
        int nextIndex = findNextPendingIndex(plan.getSteps());
        plan.setCurrentStepIndex(nextIndex >= 0 ? nextIndex : plan.getSteps().size());
        plan.setCompleted(nextIndex < 0);
        return plan;
    }

    public List<CopilotPlanStep> parsePlanSteps(JsonNode planNode) {
        List<CopilotPlanStep> steps = new ArrayList<>();
        if (planNode == null || !planNode.isArray()) {
            return steps;
        }
        int index = 0;
        for (JsonNode item : planNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            steps.add(CopilotPlanStep.builder()
                    .id(item.path("id").asText("s-" + (index + 1)))
                    .title(item.path("title").asText("步骤 " + (index + 1)))
                    .tool(textOrNull(item, "tool"))
                    .description(textOrNull(item, "description"))
                    .status(normalizeStatus(item.path("status").asText("pending")))
                    .build());
            index++;
        }
        return steps;
    }

    public CopilotActivePlan fromPlaybookPlan(CopilotActivePlan template) {
        if (template == null) {
            return null;
        }
        CopilotActivePlan copy = MAPPER.convertValue(template, CopilotActivePlan.class);
        copy.setPlanId(UUID.randomUUID().toString());
        copy.setCurrentStepIndex(0);
        copy.setCompleted(false);
        if (copy.getSteps() != null) {
            for (CopilotPlanStep step : copy.getSteps()) {
                step.setStatus("pending");
            }
        }
        return copy;
    }

    private void updateStepStatuses(CopilotActivePlan plan, List<CopilotActionResult> actionResults) {
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        Integer current = plan.getCurrentStepIndex() != null ? plan.getCurrentStepIndex() : 0;
        if (current < 0 || current >= plan.getSteps().size()) {
            return;
        }
        if (actionResults == null || actionResults.isEmpty()) {
            return;
        }
        boolean allSuccess = actionResults.stream().allMatch(CopilotActionResult::isSuccess);
        CopilotPlanStep step = plan.getSteps().get(current);
        step.setStatus(allSuccess ? "done" : "failed");
    }

    private int findNextPendingIndex(List<CopilotPlanStep> steps) {
        for (int i = 0; i < steps.size(); i++) {
            String status = steps.get(i).getStatus();
            if (status == null || "pending".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "pending";
        }
        return status.trim().toLowerCase();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }
}
