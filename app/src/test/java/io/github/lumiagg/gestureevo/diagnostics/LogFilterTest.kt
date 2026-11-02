package io.github.lumiagg.gestureevo.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filter invariants shared by the log manager list, detail and export workflows. */
class LogFilterTest {
    private val record = LogRecord(
        timestamp = 100, level = LogLevel.ERROR, module = LogModule.OVERLAY,
        event = "overlay_failure", trace = "trace-1", session = "session-1",
        fields = mapOf("package" to "example.app"), error = "IllegalStateException",
    )

    @Test
    fun allSelectedFiltersMustMatch() {
        val filter = LogFilter(since = 100, until = 100, level = LogLevel.ERROR,
            module = LogModule.OVERLAY, trace = "trace-1", session = "session-1", query = "EXAMPLE.APP")
        assertTrue(filter.matches(record))
        assertFalse(filter.copy(since = 101).matches(record))
        assertFalse(filter.copy(until = 99).matches(record))
        assertFalse(filter.copy(level = LogLevel.INFO).matches(record))
        assertFalse(filter.copy(module = LogModule.APP).matches(record))
        assertFalse(filter.copy(trace = "trace-2").matches(record))
        assertFalse(filter.copy(session = "session-2").matches(record))
    }

    @Test
    fun querySearchesMetadataAndErrorWithoutCaseSensitivity() {
        listOf("OVERLAY_FAILURE", "TRACE-1", "SESSION-1", "example.app", "illegalstateexception", "error")
            .forEach { assertTrue(it, LogFilter(query = it).matches(record)) }
        assertTrue(LogFilter(query = "   ").matches(record))
        assertFalse(LogFilter(query = "not-present").matches(record))
    }
}
