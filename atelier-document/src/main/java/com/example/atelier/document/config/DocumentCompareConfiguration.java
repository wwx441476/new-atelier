package com.example.atelier.document.config;

import com.example.atelier.document.job.DocumentCompareProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentCompareProperties.class)
public class DocumentCompareConfiguration {
}
