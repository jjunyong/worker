package com.scenamon.scenamonworker.dto

import java.io.Serializable
import java.time.LocalDateTime

data class ScenarioResultMessage(
    val executionId: Long,
    val scenarioId: Long,
    val status: String,
    val errorMessage: String?,
    val executionDurationMs: Long,
    val completedAt: LocalDateTime,
    val stepResults: List<StepResultMessage>
) : Serializable
