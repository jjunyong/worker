package com.scenamon.scenamonworker.dto

import java.io.Serializable

data class StepAssertionMessage(
    val id: Long,
    val assertionType: String,
    val selector: String,
    val expectedValue: String?
) : Serializable