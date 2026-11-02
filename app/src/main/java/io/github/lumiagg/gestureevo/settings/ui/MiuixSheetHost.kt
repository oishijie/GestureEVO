package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import androidx.compose.runtime.Composable

/**
 * Activity-owned bottom-aligned Compose window for legacy imperative selector APIs.
 * ComponentDialog supplies lifecycle/saved-state/back owners to ComposeView; its composition
 * is disposed when the dialog stops. No Material BottomSheet or XML surface is retained.
 */
fun showMiuixSheet(
    activity: Activity,
    dismissOnOutside: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (activity.isFinishing || activity.isDestroyed) return
    val owner = activity as? LifecycleOwner
    if (owner?.lifecycle?.currentState == Lifecycle.State.DESTROYED) return
    val dialog = ComponentDialog(activity)
    dialog.setCanceledOnTouchOutside(dismissOnOutside)
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
    }
    owner?.lifecycle?.addObserver(observer)
    dialog.setOnDismissListener { owner?.lifecycle?.removeObserver(observer) }
    val view = ComposeView(dialog.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MiuixEvoTheme {
                MiuixCard(
                    modifier = Modifier.fillMaxWidth()
                        .imePadding().navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .heightIn(max = activity.resources.configuration.screenHeightDp.dp * 0.9f),
                ) { content { dialog.dismiss() } }
            }
        }
    }
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(0.32f)
        setGravity(Gravity.BOTTOM)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
