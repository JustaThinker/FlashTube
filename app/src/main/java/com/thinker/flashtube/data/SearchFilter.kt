package com.thinker.flashtube.data

import androidx.compose.runtime.Immutable

enum class SearchType(val label: String, val param: String?) {
    ALL("All", null),
    VIDEO("Videos", "EgIQAQ=="),
    CHANNEL("Channels", "EgIQAg=="),
    PLAYLIST("Playlists", "EgIQAw=="),
    MOVIE("Movies", "EgIQBA=="),
}

enum class SearchDuration(val label: String, val param: String?) {
    ANY("Any", null),
    SHORT("Under 4 minutes", "EgQQARgB"),
    MEDIUM("4–20 minutes", "EgQQARgD"),
    LONG("Over 20 minutes", "EgQQARgC"),
}

enum class SearchUploadDate(val label: String, val param: String?) {
    ANY("Any time", null),
    LAST_HOUR("Last hour", "EgQIAhAB"),
    TODAY("Today", "EgQIAWAB"),
    THIS_WEEK("This week", "EgQIABAB"),
    THIS_MONTH("This month", "EgQIBBAB"),
    THIS_YEAR("This year", "EgQIBxAB"),
}

enum class SearchSortBy(val label: String, val param: String?) {
    RELEVANCE("Relevance", null),
    UPLOAD_DATE("Upload date", "CAI="),
    VIEW_COUNT("View count", "CAM="),
    RATING("Rating", "CAE="),
}

enum class SearchFeature(val label: String, val param: String) {
    LIVE("Live", "EgJAAQ=="),
    FOUR_K("4K", "EgJwAQ=="),
    HD("HD", "EgIgAQ=="),
    SUBTITLES("Subtitles/CC", "EgIoAQ=="),
    CREATIVE_COMMONS("Creative Commons", "EgIwAQ=="),
    THREE_SIXTY("360°", "EgJ4AQ=="),
    VR180("VR180", "EgP4AQE="),
    THREE_D("3D", "EgI4AQ=="),
    HDR("HDR", "EgPIAQE="),
    LOCATION("Location", "EgO4AQE="),
    PURCHASED("Purchased", "EgOIAQE="),
}

@Immutable
data class SearchFilterState(
    val type: SearchType = SearchType.ALL,
    val duration: SearchDuration = SearchDuration.ANY,
    val uploadDate: SearchUploadDate = SearchUploadDate.ANY,
    val sortBy: SearchSortBy = SearchSortBy.RELEVANCE,
    val selectedFeatures: Set<SearchFeature> = emptySet(),
) {
    val isDefault: Boolean
        get() = type == SearchType.ALL &&
                duration == SearchDuration.ANY &&
                uploadDate == SearchUploadDate.ANY &&
                sortBy == SearchSortBy.RELEVANCE &&
                selectedFeatures.isEmpty()

    /** Computes the InnerTube search `params` base64 token or null if default. */
    fun toInnerTubeParams(): String? {
        if (isDefault) return null
        // Standard single-param mappings or priority combination
        if (type.param != null && duration == SearchDuration.ANY && uploadDate == SearchUploadDate.ANY && sortBy == SearchSortBy.RELEVANCE) {
            return type.param
        }
        if (sortBy.param != null && type == SearchType.ALL && duration == SearchDuration.ANY && uploadDate == SearchUploadDate.ANY) {
            return sortBy.param
        }
        if (duration.param != null && type == SearchType.ALL && uploadDate == SearchUploadDate.ANY && sortBy == SearchSortBy.RELEVANCE) {
            return duration.param
        }
        if (uploadDate.param != null && type == SearchType.ALL && duration == SearchDuration.ANY && sortBy == SearchSortBy.RELEVANCE) {
            return uploadDate.param
        }
        // Preferred fallback precedence or combined param
        return type.param ?: sortBy.param ?: duration.param ?: uploadDate.param ?: selectedFeatures.firstOrNull()?.param
    }
}
