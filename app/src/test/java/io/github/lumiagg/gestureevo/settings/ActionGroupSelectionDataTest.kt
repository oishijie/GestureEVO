package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.gesture.config.ActionItemsTag
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionGroupSelectionDataTest {
    @Test fun duplicateDisplayNamesKeepDistinctSelectionTags() {
        val groups = listOf(
            ActionItemsTag().apply { tag = "first"; name = "同名轮盘" },
            ActionItemsTag().apply { tag = "second"; name = "同名轮盘" }
        )
        val restored = ActionItemsTag.toModelList(ActionItemsTag.toJsonList(groups))!!
        assertEquals(listOf("first", "second"), restored.map { it.tag })
        assertEquals(listOf("同名轮盘", "同名轮盘"), restored.map { it.name })
    }

    @Test fun listRoundTripPreservesOrderAndEscapedLabels() {
        val groups = listOf(
            ActionItemsTag().apply { tag = "b"; name = "\"工具\"\n组" },
            ActionItemsTag().apply { tag = "a"; name = "工作 & 日常" }
        )
        val restored = ActionItemsTag.toModelList(ActionItemsTag.toJsonList(groups))!!
        assertEquals(groups.map { it.tag }, restored.map { it.tag })
        assertEquals(groups.map { it.name }, restored.map { it.name })
    }
}
