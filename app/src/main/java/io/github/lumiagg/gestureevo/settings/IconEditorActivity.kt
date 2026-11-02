package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.ActionConfig
import io.github.lumiagg.gestureevo.gesture.ui.RoundImageTextView
import io.github.lumiagg.gestureevo.gesture.ui.applyWheelStyle
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.icon.EditableIconDrawable
import io.github.lumiagg.gestureevo.utils.icon.IconFileUtils
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.icon.IconImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class IconEditorActivity : AppCompatActivity() {
    private var sourcePath: String? = null
    private var sourceUri: String? = null
    private var icon by mutableStateOf<EditableIconDrawable?>(null)
    private var saving by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var text by mutableStateOf("")
    private var scale by mutableFloatStateOf(100f)
    private var round by mutableFloatStateOf(0f)
    private var horizontal by mutableFloatStateOf(0f)
    private var vertical by mutableFloatStateOf(0f)
    private val textMode get() = intent.getStringExtra(EXTRA_TEXT_ICON) != null
    private val wheelStyle by lazy {
        intent.getStringExtra(EXTRA_WHEEL_STYLE)?.let(ActionConfig::toModel)
    }

    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            finish()
        } else {
            sourceUri = uri.toString()
            loadUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourcePath = savedInstanceState?.getString(KEY_SOURCE)
        sourceUri = savedInstanceState?.getString(KEY_URI)
        text = savedInstanceState?.getString(KEY_TEXT) ?: intent.getStringExtra(EXTRA_TEXT_ICON).orEmpty()
        scale = (savedInstanceState?.getFloat(KEY_SCALE) ?: 100f).coerceIn(25f, 300f)
        round = (savedInstanceState?.getFloat(KEY_ROUND) ?: 0f).coerceIn(0f, 100f)
        horizontal = (savedInstanceState?.getFloat(KEY_X) ?: 0f).coerceIn(-100f, 100f)
        vertical = (savedInstanceState?.getFloat(KEY_Y) ?: 0f).coerceIn(-100f, 100f)
        loading = !textMode
        setContent { MiuixEvoTheme { EditorScreen() } }
        if (textMode) return
        val path = sourcePath
        if (path != null) {
            lifecycleScope.launch {
                showSource(withContext(Dispatchers.IO) { IconImageIO.read(path) })
            }
        } else if (sourceUri != null) {
            loadUri(Uri.parse(sourceUri))
        } else if (intent.getStringExtra(EXTRA_APP_PACKAGE) != null) {
            loadAppIcon(requireNotNull(intent.getStringExtra(EXTRA_APP_PACKAGE)))
        } else if (savedInstanceState == null) {
            gallery.launch("image/*")
        }
    }

    @Composable
    private fun EditorScreen() {
        MiuixScreen(
            title = stringResource(R.string.icon_editor_title),
            onBack = { onBackPressedDispatcher.onBackPressed() },
        ) {
            item(key = "preview") {
                MiuixCard {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        // Only the runtime wheel renderer is hosted as a View. Keep its real item
                        // diameter and scale the entire View, including text fitting and badges.
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            factory = { context ->
                                FrameLayout(context).apply {
                                    clipChildren = false
                                    clipToPadding = false
                                    val size = wheelStyle?.itemDiameter?.coerceAtLeast(1f) ?: 60f.dp2px
                                    addView(RoundImageTextView(context).apply {
                                        contentDescription = getString(R.string.icon_editor_preview)
                                        layoutParams = FrameLayout.LayoutParams(
                                            size.toInt().coerceAtLeast(1), size.toInt().coerceAtLeast(1), Gravity.CENTER,
                                        )
                                        scaleX = 160f.dp2px / size
                                        scaleY = 160f.dp2px / size
                                        wheelStyle?.let { applyWheelStyle(it) }
                                        if (intent.getBooleanExtra(EXTRA_IMAGE_BADGE, false)) {
                                            setBadge(R.drawable.baseline_image_24)
                                        }
                                    })
                                }
                            },
                            update = { host ->
                                val preview = host.getChildAt(0) as RoundImageTextView
                                if (textMode) preview.setText(text.trim()) else {
                                    // Read each Compose state so a slider edit invalidates this renderer.
                                    val current = icon
                                    current?.apply {
                                        this.scale = this@IconEditorActivity.scale / 100f
                                        roundPercent = round / 100f
                                        offsetX = horizontal / 100f
                                        offsetY = vertical / 100f
                                        invalidateSelf()
                                    }
                                    if (preview.imageFilterView.drawable !== current) preview.setImage(current)
                                }
                            },
                        )
                        if (loading) CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                    }
                    Text(
                        text = stringResource(when {
                            textMode -> R.string.icon_editor_text_preview_hint
                            wheelStyle != null -> R.string.icon_editor_wheel_preview
                            else -> R.string.icon_editor_preview_hint
                        }),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item(key = "controls") {
                MiuixCard {
                    if (textMode) {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            label = stringResource(R.string.icon_select_text_icon),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    } else {
                        EditorSlider(R.string.icon_editor_scale, scale, 25f..300f) { scale = it; updatePreview() }
                        EditorSlider(R.string.icon_editor_round, round, 0f..100f) { round = it; updatePreview() }
                        EditorSlider(R.string.icon_editor_horizontal, horizontal, -100f..100f) { horizontal = it; updatePreview() }
                        EditorSlider(R.string.icon_editor_vertical, vertical, -100f..100f) { vertical = it; updatePreview() }
                    }
                }
            }
            item(key = "actions") {
                MiuixCard {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = stringResource(R.string.icon_editor_reset),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            enabled = !saving,
                            onClick = {
                                if (textMode) text = intent.getStringExtra(EXTRA_TEXT_ICON).orEmpty() else {
                                    scale = 100f
                                    round = 0f
                                    horizontal = 0f
                                    vertical = 0f
                                    updatePreview()
                                }
                            },
                        )
                        TextButton(
                            text = stringResource(if (textMode) R.string.icon_editor_apply_text else R.string.icon_editor_apply),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = !saving && if (textMode) text.trim().isNotEmpty() else icon != null,
                            onClick = {
                                if (textMode) {
                                    val result = text.trim()
                                    if (result.isNotEmpty()) {
                                        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT_ICON, result))
                                        finish()
                                    }
                                } else applyIcon()
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EditorSlider(labelRes: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
        val label = stringResource(labelRes)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 16.sp, lineHeight = 22.sp)
                Text(
                    stringResource(R.string.display_percent, value.toInt()),
                    fontSize = 14.sp, lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Slider(
                value = value,
                valueRange = range,
                steps = (range.endInclusive - range.start).toInt() - 1,
                onValueChange = { onChange(it.roundToInt().toFloat().coerceIn(range)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }

    private fun loadUri(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val decoded = IconImageIO.read(this@IconEditorActivity, uri, 1024, 1024)
                    ?: return@withContext null
                try {
                    val file = File.createTempFile("icon-editor-", ".png", cacheDir)
                    if (IconImageIO.savePng(decoded, file.absolutePath)) {
                        sourcePath = file.absolutePath
                        decoded
                    } else {
                        decoded.recycle()
                        file.delete()
                        null
                    }
                } catch (e: IOException) {
                    Log.w("IconEditor", "Unable to cache selected image", e)
                    decoded.recycle()
                    null
                }
            }
            showSource(bitmap)
        }
    }

    private fun loadAppIcon(packageName: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val drawable = IconHelper.getIcon(this@IconEditorActivity, packageName)
                    ?: return@withContext null
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                val scale = (1024f / maxOf(width, height)).coerceAtMost(1f)
                drawable.toBitmap(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
                )
            }
            showSource(bitmap)
        }
    }

    private fun showSource(bitmap: Bitmap?) {
        loading = false
        if (bitmap == null) {
            Toast.makeText(this, R.string.icon_editor_load_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        icon = EditableIconDrawable(bitmap)
        updatePreview()
    }

    private fun updatePreview() {
        icon?.apply {
            scale = this@IconEditorActivity.scale / 100f
            roundPercent = round / 100f
            offsetX = horizontal / 100f
            offsetY = vertical / 100f
            invalidateSelf()
        }
    }

    private fun applyIcon() {
        val drawable = icon ?: return
        if (saving) return
        saving = true
        loading = true
        // Snapshot on the UI thread, so subsequent slider changes cannot affect the export.
        val bitmap = drawable.renderBitmap()
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val files = IconFileUtils(this@IconEditorActivity).setImgBitmap(bitmap)
                    if (files.saveIcon()) files.imgPath else null
                } finally {
                    bitmap.recycle()
                }
            }
            if (path == null) {
                saving = false
                loading = false
                Toast.makeText(this@IconEditorActivity, R.string.icon_editor_save_failed, Toast.LENGTH_LONG).show()
            } else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_ICON_PATH, path))
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SOURCE, sourcePath)
        outState.putString(KEY_URI, sourceUri)
        outState.putString(KEY_TEXT, text)
        outState.putFloat(KEY_SCALE, scale)
        outState.putFloat(KEY_ROUND, round)
        outState.putFloat(KEY_X, horizontal)
        outState.putFloat(KEY_Y, vertical)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) sourcePath?.let { File(it).delete() }
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_WHEEL_STYLE = "wheel_style"
        internal const val EXTRA_IMAGE_BADGE = "image_badge"
        internal const val EXTRA_ICON_PATH = "icon_path"
        internal const val EXTRA_APP_PACKAGE = "app_package"
        internal const val EXTRA_TEXT_ICON = "text_icon"
        private const val KEY_TEXT = "text"
        private const val KEY_SOURCE = "source"
        private const val KEY_URI = "source_uri"
        private const val KEY_SCALE = "scale"
        private const val KEY_ROUND = "round"
        private const val KEY_X = "offset_x"
        private const val KEY_Y = "offset_y"
    }
}
