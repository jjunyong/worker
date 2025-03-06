package com.scenamon.scenamonworker.dto

data class ExecutionResult(
    val success: Boolean,
    val errorMessage: String?,
    val stepResults: List<StepResultMessage>
)