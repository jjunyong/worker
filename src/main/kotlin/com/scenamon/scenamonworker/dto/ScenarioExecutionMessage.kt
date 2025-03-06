package com.scenamon.scenamonworker.dto

import java.io.Serializable
import java.time.LocalDateTime

data class ScenarioExecutionMessage(
    val executionId: Long,
    val scenarioId: Long,
    val subsystemId: Long,
    val projectId: Long,
    val name: String,
    val domain: String,
    val countryCode: String?,
    val steps: List<ScenarioStepMessage>
) : Serializable