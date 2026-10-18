package io.github.lumiagg.gestureevo.settings

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes

data class SettingsSearchEntry(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val pageTitleResId: Int,
    val targetActivity: Class<out Activity>,
    @IdRes val targetViewId: Int = View.NO_ID,
    /** 需要同时展开的多张分区卡（一级菜单入口深链用，如「设置」展开显示/功能/启动三张）。 */
    @IdRes val targetViewIds: List<Int> = emptyList(),
    val targetPreferenceKey: String? = null,
    @StringRes val summaryResId: Int? = null,
    val keywords: List<String> = emptyList()
)
