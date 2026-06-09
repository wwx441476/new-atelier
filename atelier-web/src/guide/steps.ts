import type { TourProps } from 'antd';
import type { OnboardingStep, OnboardingStepId } from './types';

export const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    id: 'datasources',
    path: '/datasources',
    menuLabel: '数据源管理',
    title: '配置数据源',
    summary: '连接业务数据库（JDBC），支持连接测试与热加载，是后续所有配置的基础。',
    tips: [
      '填写 JDBC URL、用户名和密码后，可先「测试连接」再保存',
      '保存后可通过「浏览」查看库表结构，便于后续建元数据',
    ],
  },
  {
    id: 'metadata',
    path: '/metadata',
    menuLabel: '元数据管理',
    title: '维护元数据',
    summary: '在元数据中登记逻辑表与字段，描述指标查询所依赖的物理表结构。',
    tips: [
      '先按数据源筛选，再「新建元数据表」',
      '展开表行可维护字段；支持 SQL 预览与在数据源执行建表',
    ],
  },
  {
    id: 'dimensions',
    path: '/dimensions',
    menuLabel: '维度管理',
    title: '定义维度',
    summary: '配置 LIST / TREE / TIME_DIM 维度，供指标分组与筛选使用。',
    tips: [
      '维度值可手动维护，也可从数据库表 DISTINCT 读取',
      '时间维度支持「批量生成」，按年/季/月快速生成格式化维度值',
    ],
  },
  {
    id: 'metrics',
    path: '/metrics',
    menuLabel: '指标管理',
    title: '创建指标',
    summary: '声明式定义 TABLE / COMPOSITE 指标，绑定维度并预览生成的 SQL。',
    tips: [
      '选择数据源与元数据表，配置聚合字段与维度绑定',
      '保存后可「SQL 预览」与「查询调试」验证指标是否正确',
    ],
  },
  {
    id: 'warning-rules',
    path: '/warning-rules',
    menuLabel: '预警规则',
    title: '配置预警规则',
    summary: '基于已创建的指标配置表达式预警，完成从数据到监控的闭环。',
    tips: [
      '规则表达式引用指标 code，可配置阈值与通知方式',
      '支持数据预览，在发布前验证规则命中情况',
    ],
  },
];

export function getStepById(id: OnboardingStepId): OnboardingStep {
  return ONBOARDING_STEPS.find((step) => step.id === id)!;
}

export function getStepIndex(id: OnboardingStepId): number {
  return ONBOARDING_STEPS.findIndex((step) => step.id === id);
}

export function getNextStepId(stepId: OnboardingStepId): OnboardingStepId | null {
  const index = getStepIndex(stepId);
  if (index < 0 || index >= ONBOARDING_STEPS.length - 1) {
    return null;
  }
  return ONBOARDING_STEPS[index + 1].id;
}

function target(id: string): () => HTMLElement {
  return () => document.getElementById(id) as HTMLElement;
}

export function getTourStepsForPath(pathname: string): TourProps['steps'] {
  switch (pathname) {
    case '/datasources':
      return [
        {
          title: '数据源管理',
          description: '在此配置 JDBC 连接。所有元数据、维度与指标都依赖已注册的数据源。',
          target: target('guide-page-header'),
        },
        {
          title: '新建数据源',
          description: '点击创建连接，选择数据库类型并填写 JDBC 信息。建议保存前先测试连接。',
          target: target('guide-primary-action'),
        },
        {
          title: '管理已有连接',
          description: '列表中可浏览库表、编辑或删除数据源。Demo 环境已预置 ds-demo 可直接体验。',
          target: target('guide-main-content'),
        },
      ];
    case '/metadata':
      return [
        {
          title: '元数据管理',
          description: '元数据表描述业务逻辑表结构，是指标 SQL 编译的输入。',
          target: target('guide-page-header'),
        },
        {
          title: '按数据源筛选',
          description: '先选择数据源，缩小表列表范围，再为对应库创建元数据表。',
          target: target('guide-filter'),
        },
        {
          title: '新建元数据表',
          description: '登记表编码、物理表名与字段。展开行可维护字段定义。',
          target: target('guide-primary-action'),
        },
        {
          title: '字段与 DDL',
          description: '展开表可添加字段；支持 SQL 预览与在目标数据源执行建表 DDL。',
          target: target('guide-main-content'),
        },
      ];
    case '/dimensions':
      return [
        {
          title: '维度管理',
          description: '维度用于指标的分组与过滤，如部门、财年等分析视角。',
          target: target('guide-page-header'),
        },
        {
          title: '新建维度',
          description: '选择维度类型与值来源。表来源从物理表读取；手动来源可自行维护维度值。',
          target: target('guide-primary-action'),
        },
        {
          title: '维护维度值',
          description: '展开维度行可查看/编辑维度值。时间维度可使用「批量生成」按格式快速填充年/季/月。',
          target: target('guide-main-content'),
        },
      ];
    case '/metrics':
      return [
        {
          title: '指标管理',
          description: '在此定义业务指标，系统将根据元数据与维度绑定自动生成查询 SQL。',
          target: target('guide-page-header'),
        },
        {
          title: '新建指标',
          description: '选择数据源、关联表与聚合字段，并绑定已创建的维度。',
          target: target('guide-primary-action'),
        },
        {
          title: '验证指标',
          description: '保存后在操作列可 SQL 预览与查询调试，确认指标逻辑正确后再用于预警。',
          target: target('guide-main-content'),
        },
      ];
    case '/warning-rules':
      return [
        {
          title: '预警规则',
          description: '最后一步：基于指标配置预警表达式，实现指标监控闭环。',
          target: target('guide-page-header'),
        },
        {
          title: '新建规则',
          description: '引用指标 code 编写表达式，设置阈值与启用状态。',
          target: target('guide-primary-action'),
        },
        {
          title: '预览与发布',
          description: '通过数据预览验证规则命中情况，确认无误后启用规则。',
          target: target('guide-main-content'),
        },
      ];
    default:
      return [];
  }
}
