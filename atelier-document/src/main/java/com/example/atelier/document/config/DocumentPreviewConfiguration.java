package com.example.atelier.document.config;

import com.example.atelier.document.job.DocumentPreviewProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentPreviewProperties.class)
public class DocumentPreviewConfiguration {
}
