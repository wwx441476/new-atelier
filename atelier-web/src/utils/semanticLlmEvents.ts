/** LLM 配置变更后通知各页面刷新状态 */
export const SEMANTIC_LLM_UPDATED_EVENT = 'atelier:semantic-llm-updated';

export function notifySemanticLlmUpdated() {
  window.dispatchEvent(new CustomEvent(SEMANTIC_LLM_UPDATED_EVENT));
}
