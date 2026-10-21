package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanFactTest {
    @Test fun dropsBoilerplateSentences() {
        val t = "This is one of thirty-five works that comprise the Winterbotham Collection. Click here to learn more about the collection."
        assertEquals("This is one of thirty-five works that comprise the Winterbotham Collection.", cleanFact(t))
    }

    @Test fun caseInsensitiveAndKeepsOthers() {
        assertEquals("A fine pastel. Painted in 1879.", cleanFact("A fine pastel. LEARN MORE at the site. Painted in 1879."))
    }

    @Test fun nullWhenNothingRemains() {
        assertNull(cleanFact("Click here to see more."))
        assertNull(cleanFact("  "))
    }
}
