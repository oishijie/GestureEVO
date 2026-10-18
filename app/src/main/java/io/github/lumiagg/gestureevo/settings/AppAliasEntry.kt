package io.github.lumiagg.gestureevo.settings

import android.content.Context
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.utils.app.appDisplayName

/**
 * 应用别名的备份载体。
 *
 * 原先挂在 [DataBackActivity] 内部：备份恢复要序列化它，索引重建也要读它。
 * 重建逻辑迁到 [AdvancedSettingsActivity] 后提到顶层，两页共用同一份定义。
 */
internal data class AppAliasEntry(
    val pkgName: String,
    val alias: String,
) {
    companion object {
        /**
         * 从应用列表里挑出「真正设置了自定义别名」的条目。
         * 别名与系统显示名一致视为未自定义，不计入备份/索引。
         */
        internal fun collect(context: Context, apps: List<APPModel>): List<AppAliasEntry> {
            return apps.mapNotNull { app ->
                val alias = app.name.trim()
                if (alias.isEmpty()) {
                    return@mapNotNull null
                }
                val defaultName = appDisplayName(context, app.pkgName)?.trim().orEmpty()
                if (alias == defaultName) {
                    null
                } else {
                    AppAliasEntry(pkgName = app.pkgName, alias = alias)
                }
            }
        }
    }
}
