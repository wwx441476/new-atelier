package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardGenerateResponse {

    /** 助手说明 */
    private String reply;

    /** 生成的大屏定义 */
    private DashboardScreen dashboard;

    /** 是否已持久化 */
    private boolean saved;
}
