package com.dynorix.gaimebridge.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", applicationName);
        response.put("status", "RUNNING");
        response.put("officialMode", "dev");
        response.put("profilesHint", "Use --spring.profiles.active=dev for the local real-data run");
        response.put("officialFlow", "portal login -> company selection -> sync -> documents");
        response.put("operatorUi", "/ui/operator.html");
        response.put("swagger", "/swagger-ui/index.html");
        response.put("health", "/actuator/health");
        response.put("sync", "/api/v1/sync/documents");
        response.put("documents", "/api/v1/documents");
        response.put("exports", "/api/v1/exports");
        response.put("exportFilePattern", "/api/v1/exports/{jobId}/file");
        return response;
    }
}
