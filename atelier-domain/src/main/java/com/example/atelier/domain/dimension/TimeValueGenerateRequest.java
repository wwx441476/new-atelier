package com.example.atelier.domain.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间维度值批量生成请求。
 * <p>
 * 格式占位符：YYYY(四位年)、YY(两位年)、MM(两位月)、M(月)、QN(Q1~Q4)、Q(季度序号)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeValueGenerateRequest {

    private TimeGranularity granularity;

    private int startYear;

    private int endYear;

    /** 月起始，仅 MONTH 粒度使用，1-12 */
    private Integer startMonth;

    /** 月结束，仅 MONTH 粒度使用，1-12 */
    private Integer endMonth;

    private String codeFormat;

    private String nameFormat;

    /** 为 true 时跳过已存在相同编码的维度值 */
    private boolean skipExisting = true;
}
