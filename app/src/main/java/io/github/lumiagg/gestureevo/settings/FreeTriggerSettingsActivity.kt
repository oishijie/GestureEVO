package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.EventBusManager.produceFreeTriggerEvent
import io.github.lumiagg.gestureevo.gesture.FreeTriggerEvent
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerDataManager
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

class FreeTriggerSettingsActivity : AppCompatActivity() {
    private lateinit var triggerConfigLauncher: ActivityResultLauncher<Intent>
    private var reloadKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerTriggerConfigLauncher()
        registerSelectionLauncher()
        setContent {
            MiuixEvoTheme {
                TriggerScreen(
                    reloadKey = reloadKey,
                    onBack = { finish() },
                    onEdit = { trigger -> showTriggerConfig(trigger) },
                    onAdd = { addNewTrigger() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadKey++
        lifecycleScope.launch {
            produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true))
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            produceFreeTriggerEvent(FreeTriggerEvent(isConfig = false))
        }
    }

    private fun addNewTrigger() {
        val trigger = FreeTriggerConfig().apply {
            name = getString(R.string.free_trigger_new_name)
            tag = FreeTriggerDataManager.buildTag()
            setState(FreeTriggerConfig.FLAG_PORTRAIT, true)
            setState(FreeTriggerConfig.FLAG_LANDSCAPE, true)
            width = 50
            height = 50
        }

        lifecycleScope.launch {
            FreeTriggerDataManager.addTrigger(this@FreeTriggerSettingsActivity, trigger)
            reloadKey++
            showTriggerConfig(trigger)
            produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true))
        }
    }

    private fun showTriggerConfig(trigger: FreeTriggerConfig) {
        triggerConfigLauncher.launch(FreeTriggerConfigActivity.createIntent(this, trigger))
    }

    private fun registerTriggerConfigLauncher() {
        triggerConfigLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            reloadKey++
        }
    }

    var selectionGestureActionCallback = object : SelectionGestureActionCallback {}

    private fun registerSelectionLauncher() {
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                selectionGestureActionCallback.onSelected()
            }
        }
    }

    @Composable
    private fun TriggerScreen(
        reloadKey: Int,
        onBack: () -> Unit,
        onEdit: (FreeTriggerConfig) -> Unit,
        onAdd: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val triggers = remember { mutableStateListOf<FreeTriggerConfig>() }

        LaunchedEffect(reloadKey) {
            triggers.clear()
            triggers.addAll(FreeTriggerDataManager.readTriggers(context))
        }

        MiuixScreen(
            title = stringResource(R.string.free_trigger_settings_title),
            onBack = onBack,
        ) {
            item {
                MiuixCard {
                    triggers.forEach { trigger ->
                        SwitchPreference(
                            title = trigger.name,
                            summary = stringResource(
                                if (trigger.enable) R.string.free_trigger_item_enabled_summary
                                else R.string.free_trigger_item_disabled_summary
                            ),
                            checked = trigger.enable,
                            onCheckedChange = { isChecked ->
                                if (trigger.enable != isChecked) {
                                    trigger.enable = isChecked
                                    scope.launch {
                                        FreeTriggerDataManager.updateTrigger(context, trigger)
                                        produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true))
                                    }
                                }
                            },
                            endActions = {
                                top.yukonga.miuix.kmp.basic.IconButton(onClick = { onEdit(trigger) }) {
                                    top.yukonga.miuix.kmp.basic.Icon(
                                        imageVector = ImageVector.vectorResource(
                                            R.drawable.baseline_edit_24
                                        ),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item {
                MiuixCard {
                    ArrowPreference(
                        title = stringResource(R.string.free_trigger_add_trigger),
                        onClick = onAdd,
                    )
                }
            }
        }
    }
}

interface SelectionGestureActionCallback {
    fun onSelected() {
    }
}
