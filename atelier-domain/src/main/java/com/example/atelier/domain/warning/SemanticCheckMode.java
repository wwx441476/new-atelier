package com.example.atelier.domain.warning;

/**
 * 语义子条件判定极性。
 */
public enum SemanticCheckMode {

    /** 文本违反策略时子条件为 true */
    VIOLATION,

    /** 文本符合策略时子条件为 true */
    REQUIREMENT
}
