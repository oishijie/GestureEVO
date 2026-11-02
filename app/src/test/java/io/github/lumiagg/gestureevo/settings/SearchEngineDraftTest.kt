package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.gesture.config.IconType
import io.github.lumiagg.gestureevo.search.config.EngineType
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SearchEngineDraftTest {
    @Test
    fun draftRoundTripPreservesEveryExecutionMode() {
        EngineType.entries.forEach { mode ->
            val original = SearchEngine().apply {
                tag = "draft"
                name = "Example"
                engineType = mode
                targetPackage = "example.app"
                targetActivity = "example.app.Share"
                searchLink = "https://example.test/?q=%s"
                externJumpPackage = "external.app"
                externJumpLink = "example://search/%s"
                autoInputEnter = false
                useCustomTabs = true
                iconType = IconType.TEXT
                textIcon = "E"
                showInSearchEngineList = false
            }
            val restored = SearchEngine.toModel(original.toJson())
            assertNotNull(restored)
            restored!!
            assertEquals(original.toJson(), restored.toJson())
            assertEquals(mode, restored.engineType)
            assertFalse(restored.autoInputEnter)
        }
    }

    @Test
    fun exportDoesNotLeakPrivateIconPath() {
        val engine = SearchEngine().apply {
            name = "Example"
            iconType = IconType.URI
            icon = "/data/user/0/private/icon.png"
        }
        val json = SearchEngine.toExportJsonList(listOf(engine))
        assertFalse(json.contains("/data/user/0"))
        val restored = SearchEngine.toModelList(json)!!.single()
        assertEquals(IconType.TEXT, restored.iconType)
        assertEquals("E", restored.textIcon)
        assertEquals("Example", restored.name)
    }
}
