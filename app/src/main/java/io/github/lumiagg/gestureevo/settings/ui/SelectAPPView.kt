package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.drake.brv.BindingAdapter
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.db.updateDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kept for callers which use this public model independently of the selector.
data class ShowItem(val appModel: APPModel, val icon: Drawable?)

class DefaultSelectAPPBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    selectAPPCallBack: SelectAPPCallBack
) : BaseSelectAPPBottomSheet(activity, coroutineScope, selectAPPCallBack) {
    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {}
}

class ShortcutSelectAPPBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    selectAPPCallBack: SelectAPPCallBack
) : BaseSelectAPPBottomSheet(activity, coroutineScope, selectAPPCallBack) {
    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {}

    override suspend fun loadAppModels(flag: Int): List<APPModel> {
        val providers = activity.packageManager
            .queryIntentActivities(Intent("android.intent.action.CREATE_SHORTCUT"), 0)
        val apps = ArrayList<APPModel>()
        // Preserve PackageManager provider order and its per-activity entries, but make
        // the visible user/system selector effective for shortcut providers as well.
        for (provider in providers) {
            val pkg = provider.activityInfo?.packageName ?: continue
            val app = getAPPDao().findByPkg(pkg) ?: continue
            if (app.getState(flag)) apps.add(app)
        }
        return apps
    }
}

class SelectActivityBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    private val selectActivityCallBack: SelectActivityCallBack,
    private val activityFilter: ActivityFilter? = null
) : BaseSelectAPPBottomSheet(activity, coroutineScope, object : SelectAPPCallBack {}) {
    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {}

    override suspend fun loadAppModels(flag: Int): List<APPModel> {
        val apps = getAPPDao().getAll(flag)
        val filter = activityFilter ?: return apps
        val pm = activity.packageManager
        return apps.filter { filter(pm, it.pkgName).isNotEmpty() }
    }

    override fun onItemClick(model: APPModel) {
        // Keep the app picker underneath; cancelling the Activity picker returns to it.
        // Only a completed Activity selection closes both levels, just as before.
        ActivitySelectBottomSheet(
            activity = activity,
            packageName = model.pkgName,
            coroutineScope = coroutineScope,
            selectActivityCallBack = object : SelectActivityCallBack {
                override fun selectCompleted(model: ActivityModel) {
                    if (!isSheetActive) return
                    selectActivityCallBack.selectCompleted(model)
                    dismissSheet()
                }
            }, activityFilter
        ).show()
    }
}

class BlackListSelectAPPBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    selectAPPCallBack: SelectAPPCallBack,
    val blackListFlag: Int = APPModel.FLAG_GESTURE_DISABLE,
    val disableIcon: Drawable? = ContextCompat.getDrawable(
        activity.applicationContext,
        R.drawable.baseline_clear_24
    )
) : BaseSelectAPPBottomSheet(activity, coroutineScope, selectAPPCallBack) {
    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {}

    override suspend fun loadAppModels(flag: Int): List<APPModel> =
        getAPPDao().getAll(flag).sortedByDescending { it.getState(blackListFlag) }

    override fun selectedIcon(model: APPModel): Drawable? =
        disableIcon.takeIf { model.getState(blackListFlag) }

    override suspend fun selectApp(model: APPModel) {
        // Work on a fresh entity: cancellation/failure must not mutate the list's live
        // object or roll back an already committed write in memory.
        val updated = withContext(Dispatchers.IO) {
            val current = getAPPDao().findById(model.id) ?: return@withContext null
            current.setState(blackListFlag, !current.getState(blackListFlag))
            updateDB(current)
            current
        } ?: return
        if (isSheetActive) {
            loadApps()
            selectAPPCallBack.selectCompleted(updated)
        }
    }
}

class BlackListWithNoDBSelectAPPBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    val blackListSelectAPPCallBack: BlackListSelectAPPCallBack,
    val blackList: MutableList<String>,
    val disableIcon: Drawable? = ContextCompat.getDrawable(
        activity.applicationContext,
        R.drawable.baseline_clear_24
    ),
    selectAPPCallBack: SelectAPPCallBack = object : SelectAPPCallBack {}
) : BaseSelectAPPBottomSheet(activity, coroutineScope, selectAPPCallBack) {
    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {}

    override suspend fun loadAppModels(flag: Int): List<APPModel> {
        val selected = blackList.toSet()
        return getAPPDao().getAll(flag).sortedBy { it.pkgName !in selected }
    }

    override fun selectedIcon(model: APPModel): Drawable? =
        disableIcon.takeIf { model.pkgName in blackList }

    override suspend fun selectApp(model: APPModel) {
        // Main-thread mutation and immutable callback snapshots avoid racing the caller's
        // persistence coroutine with subsequent toggles. This variant never updates APPModel.
        if (model.pkgName in blackList) blackList.remove(model.pkgName)
        else blackList.add(model.pkgName)
        loadApps()
        blackListSelectAPPCallBack.selectCompleted(blackList.toList())
    }

    interface BlackListSelectAPPCallBack {
        fun selectCompleted(blackList: List<String>) {}
    }
}
