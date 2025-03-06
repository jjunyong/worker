package com.scenamon.scenamonworker.dto

import java.io.Serializable
data class StepResultMessage(
    val stepOrder: Int,
    val stepId: Long,
    val status: String,
    val errorMessage: String?,
    val executionDurationMs: Long?,
    val screenshotPath: String?
) : Serializable