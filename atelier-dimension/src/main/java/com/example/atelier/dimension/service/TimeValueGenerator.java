package com.example.atelier.dimension.service;

import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.TimeGranularity;
import com.example.atelier.domain.dimension.TimeValueGenerateRequest;
import com.example.atelier.infra.exception.AtelierException;

import java.util.ArrayList;
import java.util.List;

/**
 * 按粒度与格式模板生成时间维度值列表。
 */
final class TimeValueGenerator {

    private static final int MAX_SIZE = 1000;

    private TimeValueGenerator() {
    }

    static List<DimensionValue> generate(TimeValueGenerateRequest request) {
        validate(request);
        List<TimeSlot> slots = buildSlots(request);
        if (slots.size() > MAX_SIZE) {
            throw new AtelierException("生成数量超过上限 " + MAX_SIZE + "，请缩小时间范围");
        }
        String codeFormat = request.getCodeFormat().trim();
        String nameFormat = request.getNameFormat().trim();
        List<DimensionValue> values = new ArrayList<>(slots.size());
        int sort = 1;
        for (TimeSlot slot : slots) {
            values.add(DimensionValue.builder()
                    .code(TimeValueFormatter.format(codeFormat, slot.year, slot.month, slot.quarter))
                    .name(TimeValueFormatter.format(nameFormat, slot.year, slot.month, slot.quarter))
                    .sort(sort++)
                    .build());
        }
        return values;
    }

    private static void validate(TimeValueGenerateRequest request) {
        if (request.getGranularity() == null) {
            throw new AtelierException("时间粒度不能为空");
        }
        if (request.getStartYear() > request.getEndYear()) {
            throw new AtelierException("起始年份不能大于结束年份");
        }
        if (request.getCodeFormat() == null || request.getCodeFormat().trim().isEmpty()) {
            throw new AtelierException("编码格式不能为空");
        }
        if (request.getNameFormat() == null || request.getNameFormat().trim().isEmpty()) {
            throw new AtelierException("名称格式不能为空");
        }
        if (request.getGranularity() == TimeGranularity.MONTH) {
            int startMonth = monthOrDefault(request.getStartMonth(), 1);
            int endMonth = monthOrDefault(request.getEndMonth(), 12);
            if (startMonth < 1 || startMonth > 12 || endMonth < 1 || endMonth > 12) {
                throw new AtelierException("月份必须在 1-12 之间");
            }
            if (request.getStartYear() == request.getEndYear() && startMonth > endMonth) {
                throw new AtelierException("起始月份不能大于结束月份");
            }
            if (request.getStartYear() > request.getEndYear()) {
                throw new AtelierException("起始年月不能晚于结束年月");
            }
        }
    }

    private static List<TimeSlot> buildSlots(TimeValueGenerateRequest request) {
        switch (request.getGranularity()) {
            case YEAR:
                return buildYearSlots(request.getStartYear(), request.getEndYear());
            case QUARTER:
                return buildQuarterSlots(request.getStartYear(), request.getEndYear());
            case MONTH:
                return buildMonthSlots(
                        request.getStartYear(),
                        monthOrDefault(request.getStartMonth(), 1),
                        request.getEndYear(),
                        monthOrDefault(request.getEndMonth(), 12));
            default:
                throw new AtelierException("不支持的时间粒度: " + request.getGranularity());
        }
    }

    private static List<TimeSlot> buildYearSlots(int startYear, int endYear) {
        List<TimeSlot> slots = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            slots.add(new TimeSlot(year, 1, 1));
        }
        return slots;
    }

    private static List<TimeSlot> buildQuarterSlots(int startYear, int endYear) {
        List<TimeSlot> slots = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                slots.add(new TimeSlot(year, quarter * 3 - 2, quarter));
            }
        }
        return slots;
    }

    private static List<TimeSlot> buildMonthSlots(int startYear, int startMonth, int endYear, int endMonth) {
        List<TimeSlot> slots = new ArrayList<>();
        int year = startYear;
        int month = startMonth;
        while (year < endYear || (year == endYear && month <= endMonth)) {
            slots.add(new TimeSlot(year, month, quarterOf(month)));
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        return slots;
    }

    private static int monthOrDefault(Integer month, int defaultMonth) {
        return month != null ? month : defaultMonth;
    }

    private static int quarterOf(int month) {
        return (month - 1) / 3 + 1;
    }

    private static final class TimeSlot {
        private final int year;
        private final int month;
        private final int quarter;

        private TimeSlot(int year, int month, int quarter) {
            this.year = year;
            this.month = month;
            this.quarter = quarter;
        }
    }
}
