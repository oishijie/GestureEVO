package io.github.lumiagg.gestureevo.settings.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleEventObserver
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.WebDAVSettings
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.utils.WebDAVManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun showWebDAVConfigDialog(
    activity: AppCompatActivity,
    coroutineScope: LifecycleCoroutineScope,
    onConfigured: () -> Unit
) {
    if (activity.isFinishing || activity.isDestroyed) return

    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var testing by mutableStateOf(false)
    var saving by mutableStateOf(false)
    val webDavManager = WebDAVManager()
    val dialog = ComponentDialog(activity)
    // Keep the old setCancelable(false) contract: only the explicit cancel button closes it.
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
    }
    activity.lifecycle.addObserver(observer)
    dialog.setOnDismissListener { activity.lifecycle.removeObserver(observer) }

    // Keep these operations in the caller's lifecycle scope, not the composition scope:
    // dismissing the editor must not cancel an already-started credential save.
    coroutineScope.launch {
        serverUrl = WebDAVSettings.getServerUrl(activity)
        username = WebDAVSettings.getUsername(activity)
        password = WebDAVSettings.getPassword(activity)
    }

    fun validatedCredentials(): Triple<String, String, String>? {
        val credentials = Triple(serverUrl.trim(), username.trim(), password.trim())
        if (credentials.first.isEmpty() || credentials.second.isEmpty()) {
            Toast.makeText(activity, R.string.webdav_fill_server_user, Toast.LENGTH_SHORT).show()
            return null
        }
        // An empty password remains valid; do not add URL/network validation here.
        return credentials
    }

    val view = ComposeView(dialog.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MiuixEvoTheme {
                var passwordVisible by remember { mutableStateOf(false) }
                MiuixCard(
                    modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                        .padding(12.dp)
                        .heightIn(max = activity.resources.configuration.screenHeightDp.dp * 0.9f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_config_title),
                            modifier = Modifier.padding(top = 24.dp),
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                .heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = stringResource(R.string.webdav_config_server_hint),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                            TextField(
                                value = username,
                                onValueChange = { username = it },
                                label = stringResource(R.string.webdav_config_username_hint),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            TextField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResource(R.string.webdav_config_password_hint),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (passwordVisible) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                            .semantics { selected = passwordVisible }
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (passwordVisible)
                                                    com.google.android.material.R.drawable.design_ic_visibility_off
                                                else com.google.android.material.R.drawable.design_ic_visibility
                                            ),
                                            contentDescription = stringResource(
                                                com.google.android.material.R.string.password_toggle_content_description
                                            )
                                        )
                                    }
                                }
                            )
                            Text(
                                text = stringResource(R.string.webdav_config_example),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            if (testing || saving) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            // The XML intentionally hid this action. Retain its behavior without
                            // advertising login() as a network probe (it only configures a client).
                            val showConnectionTest = false
                            if (showConnectionTest) {
                                TextButton(
                                    text = stringResource(R.string.webdav_config_test),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = !testing && !saving,
                                    onClick = {
                                        val credentials = validatedCredentials()
                                        if (credentials != null && !testing && !saving) {
                                            testing = true
                                            coroutineScope.launch {
                                                try {
                                                    val result = webDavManager.login(
                                                        credentials.first, credentials.second, credentials.third
                                                    )
                                                    if (result.isSuccess) {
                                                        Toast.makeText(activity, R.string.webdav_connect_success,
                                                            Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val message = result.exceptionOrNull()?.localizedMessage
                                                            ?: activity.getString(R.string.webdav_connect_failed)
                                                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    testing = false
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            TextButton(
                                text = stringResource(R.string.webdav_config_cancel),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                onClick = { dialog.dismiss() }
                            )
                            TextButton(
                                text = stringResource(R.string.webdav_config_save),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                enabled = !testing && !saving,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    val credentials = validatedCredentials()
                                    if (credentials != null && !testing && !saving) {
                                        saving = true
                                        coroutineScope.launch {
                                            try {
                                                WebDAVSettings.saveCredentials(activity,
                                                    credentials.first, credentials.second, credentials.third)
                                                saving = false
                                                // Preserve callback-before-dismiss and exception propagation:
                                                // a failed save/callback must not report success or close here.
                                                onConfigured()
                                                dialog.dismiss()
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
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
