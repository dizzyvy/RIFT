package com.dizzyvy.rift.ui.library

internal val LIBRARY_CATEGORY_TABS = listOf(
    "Songs",
    "Artists",
    "Albums",
    "Playlists",
    "Folders",
    "Genres",
    "Years",
    "Duplicates",
)

private val SEARCH_LIBRARY_CATEGORY_TABS = listOf("Search") + LIBRARY_CATEGORY_TABS

internal fun visibleLibraryCategoryTabs(searchQuery: String): List<String> =
    if (searchQuery.isBlank()) LIBRARY_CATEGORY_TABS else SEARCH_LIBRARY_CATEGORY_TABS

internal fun adjacentCategoryForSwipe(
    categories: List<String>,
    selectedCategory: String,
    swipeRight: Boolean,
): String? {
    val selectedIndex = categories.indexOf(selectedCategory)
    if (selectedIndex < 0) return null
    val nextIndex = selectedIndex + if (swipeRight) 1 else -1
    return categories.getOrNull(nextIndex)
}
