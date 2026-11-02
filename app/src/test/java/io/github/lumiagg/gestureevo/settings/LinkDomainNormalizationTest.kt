package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.settings.ui.LinkOpenDomainUtils
import org.junit.Assert.*
import org.junit.Test

class LinkDomainNormalizationTest {
    @Test fun normalizesPlainAndFullDomains() {
        assertEquals("example.com", LinkOpenDomainUtils.normalizeDomainInput(" WWW.Example.COM "))
        assertEquals("example.com", LinkOpenDomainUtils.normalizeDomainInput("https://www.example.com/path?q=a"))
        assertEquals("sub.example.com", LinkOpenDomainUtils.normalizeDomainInput("//sub.example.com/a"))
    }

    @Test fun matchesExactAndSubdomainButNotSuffixLookalikes() {
        assertTrue(LinkOpenDomainUtils.matches("https://example.com/a", "example.com"))
        assertTrue(LinkOpenDomainUtils.matches("https://news.example.com/a", "example.com"))
        assertFalse(LinkOpenDomainUtils.matches("https://notexample.com/a", "example.com"))
        assertFalse(LinkOpenDomainUtils.matches("https://example.com.attacker.test/a", "example.com"))
    }

    @Test fun rejectsEmptyAndNonDomainInput() {
        assertNull(LinkOpenDomainUtils.normalizeDomainInput(""))
        assertNull(LinkOpenDomainUtils.normalizeDomainInput("localhost"))
        assertNull(LinkOpenDomainUtils.normalizeDomainInput("not a domain"))
    }
}
