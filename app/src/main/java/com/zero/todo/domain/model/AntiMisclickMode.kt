package com.zero.todo.domain.model

/**
 * Configurable anti-misclick completion mode for [com.zero.todo.feature.tasklist.AntiMisclickTaskCard].
 */
enum class AntiMisclickMode(
    val titleZh: String,
    val descriptionZh: String
) {
    HOLD_CONFIRM(
        titleZh = "短按确认",
        descriptionZh = "按住左侧复选框 0.3 秒径向蓄力完成，禁用滑动完成"
    ),
    SWIPE_DAMPED(
        titleZh = "滑动阻尼",
        descriptionZh = "左右滑动卡片超过 30% 阻尼阈值触发完成，禁用按压复选框"
    ),
    BOTH(
        titleZh = "双模式",
        descriptionZh = "同时支持 0.3 秒短按蓄力与 30% 滑动阻尼完成"
    );

    val allowHoldConfirm: Boolean
        get() = this == HOLD_CONFIRM || this == BOTH

    val allowSwipeComplete: Boolean
        get() = this == SWIPE_DAMPED || this == BOTH

    companion object {
        fun fromStorage(raw: String?): AntiMisclickMode =
            entries.firstOrNull { it.name == raw } ?: BOTH
    }
}
