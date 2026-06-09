package com.example.atelier.dimension.service;

import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.TimeGranularity;
import com.example.atelier.domain.dimension.TimeValueGenerateRequest;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TimeValueGeneratorTest {

    @Test
    public void generateYear_plainFormat() {
        List<DimensionValue> values = TimeValueGenerator.generate(request(
                TimeGranularity.YEAR, 2022, 2024, "YYYY", "YYYY年"));
        assertEquals(3, values.size());
        assertEquals("2022", values.get(0).getCode());
        assertEquals("2022年", values.get(0).getName());
        assertEquals("2024", values.get(2).getCode());
        assertEquals("2024年", values.get(2).getName());
    }

    @Test
    public void generateYear_fiscalYearFormat() {
        List<DimensionValue> values = TimeValueGenerator.generate(request(
                TimeGranularity.YEAR, 2024, 2024, "FYYYYY", "YYYY财年"));
        assertEquals("FY2024", values.get(0).getCode());
        assertEquals("2024财年", values.get(0).getName());
    }

    @Test
    public void generateYear_twoDigitCode() {
        List<DimensionValue> values = TimeValueGenerator.generate(request(
                TimeGranularity.YEAR, 2024, 2024, "YY", "YYYY年"));
        assertEquals("24", values.get(0).getCode());
        assertEquals("2024年", values.get(0).getName());
    }

    @Test
    public void generateQuarter_formats() {
        List<DimensionValue> values = TimeValueGenerator.generate(request(
                TimeGranularity.QUARTER, 2024, 2024, "YYYYQN", "YYYY年第Q季度"));
        assertEquals(4, values.size());
        assertEquals("2024Q1", values.get(0).getCode());
        assertEquals("2024年第1季度", values.get(0).getName());
        assertEquals("2024Q4", values.get(3).getCode());
    }

    @Test
    public void generateMonth_formats() {
        TimeValueGenerateRequest request = TimeValueGenerateRequest.builder()
                .granularity(TimeGranularity.MONTH)
                .startYear(2024)
                .endYear(2024)
                .startMonth(1)
                .endMonth(2)
                .codeFormat("YYYY-MM")
                .nameFormat("YYYY年M月")
                .build();
        List<DimensionValue> values = TimeValueGenerator.generate(request);
        assertEquals(2, values.size());
        assertEquals("2024-01", values.get(0).getCode());
        assertEquals("2024年1月", values.get(0).getName());
        assertEquals("2024-02", values.get(1).getCode());
    }

    private static TimeValueGenerateRequest request(TimeGranularity granularity,
                                                    int startYear,
                                                    int endYear,
                                                    String codeFormat,
                                                    String nameFormat) {
        return TimeValueGenerateRequest.builder()
                .granularity(granularity)
                .startYear(startYear)
                .endYear(endYear)
                .codeFormat(codeFormat)
                .nameFormat(nameFormat)
                .build();
    }
}
