package com.example.atelier.api.copilot;

import com.example.atelier.domain.copilot.CopilotActivePlan;
import com.example.atelier.domain.copilot.CopilotPlanStep;
import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.domain.copilot.CopilotPlaybookStep;
import com.example.atelier.infra.persistence.service.CopilotPlaybookService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class CopilotPlaybookMatcher {

    private final CopilotPlaybookService playbookService;

    public CopilotPlaybookMatcher(CopilotPlaybookService playbookService) {
        this.playbookService = playbookService;
    }

    public List<CopilotPlaybook> match(String userText, int limit) {
        if (userText == null || userText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = userText.toLowerCase(Locale.ROOT);
        List<ScoredPlaybook> scored = new ArrayList<>();
        for (CopilotPlaybook playbook : playbookService.listAll()) {
            if (Boolean.FALSE.equals(playbook.getEnabled())) {
                continue;
            }
            int score = scorePlaybook(normalized, playbook);
            if (score > 0) {
                scored.add(new ScoredPlaybook(playbook, score));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredPlaybook::getScore).reversed());
        List<CopilotPlaybook> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            result.add(scored.get(i).getPlaybook());
        }
        return result;
    }

    public CopilotActivePlan toActivePlan(CopilotPlaybook playbook) {
        if (playbook == null) {
            return null;
        }
        List<CopilotPlanStep> steps = new ArrayList<>();
        if (playbook.getSteps() != null) {
            int index = 0;
            for (CopilotPlaybookStep step : playbook.getSteps()) {
                steps.add(CopilotPlanStep.builder()
                        .id("s-" + (index + 1))
                        .title(step.getTitle())
                        .tool(step.getTool())
                        .description(step.getDescription())
                        .status("pending")
                        .build());
                index++;
            }
        }
        return CopilotActivePlan.builder()
                .planId(UUID.randomUUID().toString())
                .playbookId(playbook.getId())
                .playbookName(playbook.getName())
                .steps(steps)
                .currentStepIndex(0)
                .completed(false)
                .build();
    }

    private int scorePlaybook(String normalizedUserText, CopilotPlaybook playbook) {
        int score = 0;
        if (playbook.getName() != null && normalizedUserText.contains(playbook.getName().toLowerCase(Locale.ROOT))) {
            score += 5;
        }
        if (playbook.getTriggerKeywords() != null) {
            for (String keyword : playbook.getTriggerKeywords()) {
                if (keyword != null && normalizedUserText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score += 3;
                }
            }
        }
        if (playbook.getDescription() != null) {
            for (String token : playbook.getDescription().toLowerCase(Locale.ROOT).split("[\\s，,、]+")) {
                if (token.length() >= 2 && normalizedUserText.contains(token)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private static final class ScoredPlaybook {
        private final CopilotPlaybook playbook;
        private final int score;

        private ScoredPlaybook(CopilotPlaybook playbook, int score) {
            this.playbook = playbook;
            this.score = score;
        }

        private CopilotPlaybook getPlaybook() {
            return playbook;
        }

        private int getScore() {
            return score;
        }
    }
}
