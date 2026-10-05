package com.system_server.ai_demo.commons.validation;

/**
 * {@link DateRange} が「自（from）」「至（to）」のどちらを境界として扱うか（＝どちらの項目にエラーを紐づけ、どの向きのメッセージを
 * 表示するか）を指定する区分。いずれのモードでも検証する不変条件は同一（自 ≤ 至）で、変わるのは表現のみ。
 */
public enum DateBound {

    /** 自（from）を下限として扱う。至（to）が自より前なら至の項目にエラーを表示する（「{boundLabel}以降の日付を…」）。 */
    LOWER,

    /** 至（to）を上限として扱う。自（from）が至より後なら自の項目にエラーを表示する（「{boundLabel}以前の日付を…」）。 */
    UPPER
}
