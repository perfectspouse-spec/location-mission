package com.example.gemini

data class PlaceSearchResult(
    val placeName: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val summary: String,
    val suggestedTasks: List<String> = emptyList(),
    val mapsUrl: String? = null,
    val isGrounded: Boolean = true
)
