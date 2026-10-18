package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityHiddenSearchResultsBinding
import io.github.lumiagg.gestureevo.databinding.HiddenSearchResultItemBinding
import io.github.lumiagg.gestureevo.db.HiddenSearchResultModel
import io.github.lumiagg.gestureevo.db.getHiddenSearchResultDao
import io.github.lumiagg.gestureevo.search.typeLabelRes
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 已隐藏的搜索结果：在所有查询中被隐藏的结果可在此恢复显示。
 *
 * 从搜索页菜单进入；列表随数据库实时刷新。
 */
class HiddenSearchResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHiddenSearchResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenSearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.hiddenResultRecycler.linear().setup {
            addType<HiddenSearchResultModel>(R.layout.hidden_search_result_item)
            onBind {
                val itemBinding = HiddenSearchResultItemBinding.bind(itemView)
                val model = getModel<HiddenSearchResultModel>()
                itemBinding.resultName.text = model.displayName
                itemBinding.resultType.setText(model.typeLabelRes())
                itemBinding.resultIcon.clearTint()
                if (model.resultKey.startsWith("app:")) {
                    val packageName = model.resultKey.substringAfter(':').substringAfter(':')
                    itemBinding.resultIcon.setImage(IconHelper.getIcon(this@HiddenSearchResultsActivity, packageName))
                } else {
                    itemBinding.resultIcon.setText(model.displayName.take(1))
                }
                itemBinding.restoreResult.contentDescription = getString(
                    R.string.hidden_search_result_restore_description, model.displayName
                )
            }
            onClick(R.id.restore_result) {
                restoreHiddenResult(getModel<HiddenSearchResultModel>())
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                getHiddenSearchResultDao().observeAll().collect { results ->
                    binding.hiddenResultRecycler.models = results
                    binding.hiddenResultsEmpty.visibility =
                        if (results.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun restoreHiddenResult(result: HiddenSearchResultModel) {
        lifecycleScope.launch {
            try {
                getHiddenSearchResultDao().delete(result.resultKey)
                Toast.makeText(
                    this@HiddenSearchResultsActivity,
                    R.string.hidden_search_result_restored,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@HiddenSearchResultsActivity,
                    R.string.hidden_search_result_update_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
