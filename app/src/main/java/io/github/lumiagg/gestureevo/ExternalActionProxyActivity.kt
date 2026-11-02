package io.github.lumiagg.gestureevo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.db.ExternalActionPermissionModel
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.utils.ActionProxy
import io.github.lumiagg.gestureevo.utils.ExternalActionPermissionStore
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import io.github.lumiagg.gestureevo.utils.ToastHelper
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ExternalActionProxyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            showToastAndFinish(R.string.action_proxy_link_missing)
            return
        }

        when (uri.host) {
            "action" -> handleDirectActionLink(uri)
            else -> showToastAndFinish(R.string.action_proxy_link_missing)
        }
    }

    private fun handleDirectActionLink(uri: Uri) {
        val link = uri.toString()
        val callerPackage = resolveCallerPackage()
        if (callerPackage == packageName) {
            lifecycleScope.launch {
                executeAndFinish(link)
            }
            return
        }

        lifecycleScope.launch {
            if (!callerPackage.isNullOrBlank() &&
                ExternalActionPermissionStore.isAlwaysAllowed(callerPackage, link)
            ) {
                executeAndFinish(link)
                return@launch
            }

            showAuthorizationDialog(link, callerPackage)
        }
    }

    private fun showAuthorizationDialog(link: String, callerPackage: String?) {
        if (isFinishing || isDestroyed) return
        val callerName = resolveAppName(callerPackage)
        val canRememberPermission = !callerPackage.isNullOrBlank()
        val dialogMessage = buildDialogMessage(link, canRememberPermission)
        // ComponentDialog installs lifecycle, saved-state and back owners for ComposeView.
        // Keep action execution in the Activity scope: dismissing must not cancel it.
        val dialog = ComponentDialog(this)
        var actionStarted = false
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { finish() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener { lifecycle.removeObserver(observer) }

        val view = ComposeView(dialog.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MiuixEvoTheme {
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                            .heightIn(max = resources.configuration.screenHeightDp.dp * 0.9f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.action_proxy_permission_inferred_caller_title, callerName
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                textAlign = TextAlign.Center,
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
                                Text(
                                    text = dialogMessage,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                TextButton(
                                    text = stringResource(R.string.action_proxy_permission_allow_action),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    enabled = canRememberPermission,
                                    onClick = {
                                        if (actionStarted) return@TextButton
                                        actionStarted = true
                                        rememberPermissionAndExecute(
                                            dismiss = { dialog.dismiss() },
                                            callerPackage = callerPackage,
                                            link = link,
                                            scopeType = ExternalActionPermissionModel.ScopeType.ACTION
                                        )
                                    }
                                )
                                TextButton(
                                    text = stringResource(R.string.action_proxy_permission_allow_link),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    enabled = canRememberPermission,
                                    onClick = {
                                        if (actionStarted) return@TextButton
                                        actionStarted = true
                                        rememberPermissionAndExecute(
                                            dismiss = { dialog.dismiss() },
                                            callerPackage = callerPackage,
                                            link = link,
                                            scopeType = ExternalActionPermissionModel.ScopeType.LINK
                                        )
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                TextButton(
                                    text = stringResource(R.string.data_backup_cancel),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    onClick = {
                                        dialog.dismiss()
                                        finish()
                                    }
                                )
                                TextButton(
                                    text = stringResource(R.string.action_proxy_permission_allow_once),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        if (actionStarted) return@TextButton
                                        actionStarted = true
                                        dialog.dismiss()
                                        lifecycleScope.launch { executeAndFinish(link) }
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
            setGravity(Gravity.CENTER)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun buildDialogMessage(link: String, canRememberPermission: Boolean): String {
        val actionLabel = GestureActionLink().apply { parseLink(link) }.actionType.showName(this)
        val baseMessage = getString(
            R.string.action_proxy_permission_message,
            actionLabel,
            link
        )
        val callerNotice = if (!canRememberPermission) {
            getString(R.string.action_proxy_permission_unknown_caller_once_only)
        } else {
            getString(R.string.action_proxy_permission_inferred_caller_notice)
        }
        return "$baseMessage\n\n$callerNotice"
    }

    private fun rememberPermissionAndExecute(
        dismiss: () -> Unit,
        callerPackage: String?,
        link: String,
        scopeType: ExternalActionPermissionModel.ScopeType
    ) {
        lifecycleScope.launch {
            if (callerPackage.isNullOrBlank()) {
                ToastHelper.showInUIThread(
                    applicationContext,
                    getString(R.string.action_proxy_permission_unknown_caller_once_only)
                )
            } else {
                when (scopeType) {
                    ExternalActionPermissionModel.ScopeType.ACTION -> {
                        ExternalActionPermissionStore.allowAlwaysForAction(callerPackage, link)
                    }

                    ExternalActionPermissionModel.ScopeType.LINK -> {
                        ExternalActionPermissionStore.allowAlwaysForLink(callerPackage, link)
                    }
                }
                ToastHelper.showInUIThread(
                    applicationContext,
                    getString(R.string.action_proxy_permission_saved)
                )
            }
            dismiss()
            executeAndFinish(link)
        }
    }

    private suspend fun executeAndFinish(link: String) {
        ActionProxy(applicationContext, link).execute()
        finish()
    }

    private fun showToastAndFinish(messageRes: Int) {
        ToastHelper.showInUIThread(applicationContext, getString(messageRes))
        finish()
    }

    private fun resolveCallerPackage(): String? {
        callingPackage?.takeIf { it.isNotBlank() }?.let { return it }

        readReferrerUri()?.let { referrerUri ->
            if (referrerUri.scheme == "android-app") {
                referrerUri.host?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
            ?.takeIf { it.startsWith("android-app://") }
            ?.removePrefix("android-app://")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    @Suppress("DEPRECATION")
    private fun readReferrerUri(): Uri? {
        return intent.getParcelableExtra(Intent.EXTRA_REFERRER) ?: referrer
    }

    private fun resolveAppName(packageName: String?): String {
        val resolvedPackage = packageName?.takeIf { it.isNotBlank() }
            ?: return getString(R.string.action_proxy_permission_unknown_caller)
        return try {
            val appInfo = packageManager.getApplicationInfo(resolvedPackage, 0)
            packageManager.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() }
                ?.let { "$it ($resolvedPackage)" }
                ?: resolvedPackage
        } catch (_: Exception) {
            resolvedPackage
        }
    }
}
