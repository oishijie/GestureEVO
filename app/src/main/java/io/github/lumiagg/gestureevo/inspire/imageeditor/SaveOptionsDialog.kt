package io.github.lumiagg.gestureevo.inspire.imageeditor

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import top.yukonga.miuix.kmp.basic.TextButton

/** Activity-owned menu; choosing a row never puts export work in composition scope. */
internal fun showMiuixSaveOptions(activity: AppCompatActivity, save: (Long) -> Unit): Dialog {
    val dialog = ComponentDialog(activity)
    var submitted = false
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
    }
    activity.lifecycle.addObserver(observer)
    dialog.setOnDismissListener { activity.lifecycle.removeObserver(observer) }
    dialog.setCanceledOnTouchOutside(true)
    dialog.setContentView(ComposeView(dialog.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MiuixEvoTheme {
                MiuixCard(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(R.string.inspire_image_edit_save_persist to 0L,
                            R.string.inspire_image_edit_save_auto_delete to 5L).forEach { (label, minutes) ->
                            TextButton(text = stringResource(label), modifier = Modifier.fillMaxWidth(), minHeight = 48.dp,
                                onClick = {
                                    if (!submitted) {
                                        submitted = true
                                        dialog.dismiss()
                                        save(minutes)
                                    }
                                })
                        }
                    }
                }
            }
        }
    })
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    return dialog
}
