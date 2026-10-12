package com.dizzyvy.rift.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTabNavigationTest {
    @Test
    fun swipesMoveToAdjacentCategoriesWithoutWrapping() {
        assertEquals("Artists", adjacentCategoryForSwipe(LIBRARY_CATEGORY_TABS, "Songs", swipeRight = true))
        assertNull(adjacentCategoryForSwipe(LIBRARY_CATEGORY_TABS, "Songs", swipeRight = false))
        assertEquals("Years", adjacentCategoryForSwipe(LIBRARY_CATEGORY_TABS, "Duplicates", swipeRight = false))
        assertNull(adjacentCategoryForSwipe(LIBRARY_CATEGORY_TABS, "Duplicates", swipeRight = true))
    }

    @Test
    fun searchSwipeOrderMatchesVisibleTabs() {
        val categories = visibleLibraryCategoryTabs("query")

        assertEquals(listOf("Search") + LIBRARY_CATEGORY_TABS, categories)
        assertEquals("Songs", adjacentCategoryForSwipe(categories, "Search", swipeRight = true))
    }
}
