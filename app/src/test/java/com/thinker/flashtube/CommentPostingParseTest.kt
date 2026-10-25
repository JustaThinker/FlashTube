package com.thinker.flashtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A real signed-out comments page (captured 2026-10-04): threads map to toolbars, paging works. */
class CommentPostingParseTest {
    private val page = JSONObject(javaClass.classLoader!!.getResource("comments.json")!!.readText())

    @Test fun threadsMapToToolbarEntities() {
        val parsed = CommentPosting.parsePage(page)
        assertEquals(20, parsed.threadsWithToolbar.size)
        // Signed out, the reply slot is a sign-in prompt, so no params; signed in they fill in.
        assertEquals(0, parsed.replyParams.size)
        assertNull(parsed.createComment)
    }

    @Test fun nextPageToken() {
        assertNotNull(CommentPosting.parsePage(page).next)
    }
}
