package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WarningRuleJobApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void submitPreviewJob_shouldCompleteWithResult() throws InterruptedException {
        String ruleId = findLowProfitRuleId();
        Map<String, Object> body = new HashMap<>();
        body.put("pageIndex", 1);
        body.put("pageSize", 20);
        body.put("keywordOnly", true);

        ResponseEntity<ApiResponse<WarningRuleJob>> submitResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/" + ruleId + "/preview/jobs",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<WarningRuleJob>>() {
                });
        assertEquals(200, submitResponse.getStatusCodeValue());
        WarningRuleJob submitted = submitResponse.getBody().getData();
        assertNotNull(submitted.getId());
        assertEquals(WarningRuleJobStatus.PENDING, submitted.getStatus());

        WarningRuleJob completed = waitForCompletion(submitted.getId(), 30);
        assertEquals(WarningRuleJobStatus.SUCCESS, completed.getStatus());
        assertNotNull(completed.getResult());
        assertTrue(completed.getTotal() >= 0);
    }

    private String findLowProfitRuleId() {
        ResponseEntity<ApiResponse<List<WarningRule>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<WarningRule>>>() {
                });
        return response.getBody().getData().stream()
                .filter(rule -> "low_profit".equals(rule.getCode()))
                .findFirst()
                .map(WarningRule::getId)
                .orElseThrow(() -> new IllegalStateException("seed rule low_profit not found"));
    }

    private WarningRuleJob waitForCompletion(String jobId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<ApiResponse<WarningRuleJob>> response = restTemplate.exchange(
                    "http://localhost:" + port + "/api/v2/warning/jobs/" + jobId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<WarningRuleJob>>() {
                    });
            WarningRuleJob job = response.getBody().getData();
            if (job.getStatus() == WarningRuleJobStatus.SUCCESS
                    || job.getStatus() == WarningRuleJobStatus.FAILED) {
                return job;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("job did not complete in time: " + jobId);
    }
}
