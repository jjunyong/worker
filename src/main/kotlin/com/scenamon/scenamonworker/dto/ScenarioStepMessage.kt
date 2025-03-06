package com.scenamon.scenamonworker.dto

import java.io.Serializable

data class ScenarioStepMessage(
    val id: Long,
    val stepOrder: Int,
    val action: String,
    val selector: String,
    val value: String?,
    val description: String,
    val isVerificationStep: Boolean,
    val waitAfterInMillis: Long,
    val assertions: List<StepAssertionMessage> = emptyList()
) : Serializable