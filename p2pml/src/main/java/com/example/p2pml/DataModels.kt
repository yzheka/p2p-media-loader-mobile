package com.example.p2pml

data class Stream(
    val runtimeId: String,
    val type: String,
    val index: Int,
)

data class Segment(
    val runtimeId: String,
    val externalId: Int,
    val url: String,
    val byteRange: ByteRange?,
    val startTime: Double,
    val endTime: Double,
)

data class ByteRange(
    val start: Long,
    val end: Long,
)