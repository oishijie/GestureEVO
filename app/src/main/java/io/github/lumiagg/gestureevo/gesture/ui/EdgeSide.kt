package io.github.lumiagg.gestureevo.gesture.ui

/**
 * 边缘返回触发时所处的一侧。
 *
 * 原先定义在 [io.github.lumiagg.gestureevo.gesture.ui.GestureFluidAppearance] 内部，
 * 随流体反馈移除时被误删；它其实是边缘返回（InspireView）的基础类型，与流体无关，
 * 这里独立成文件恢复。
 */
enum class EdgeSide {
    LEFT,
    RIGHT,
}
