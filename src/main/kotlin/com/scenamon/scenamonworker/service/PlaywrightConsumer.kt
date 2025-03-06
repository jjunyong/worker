package com.scenamon.scenamonworker.service

import com.scenamon.scenamonworker.dto.ScenarioExecutionMessage
import com.scenamon.scenamonworker.dto.ScenarioResultMessage
import com.scenamon.scenamonworker.dto.StepResultMessage
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PlaywrightConsumer(
    private val playwrightExecutor: PlaywrightExecutor,
    private val rabbitTemplate: RabbitTemplate
) {
    private val logger = LoggerFactory.getLogger(PlaywrightConsumer::class.java)

    @Value("\${rabbitmq.exchange.name}")
    private lateinit var exchangeName: String

    @Value("\${rabbitmq.routing.result}")
    private lateinit var resultRoutingKey: String

    @RabbitListener(queues = ["\${rabbitmq.queue.scenario}"])
    fun receiveScenario(message: ScenarioExecutionMessage) {
        logger.info("Received scenario execution request: ${message.executionId}")

        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<StepResultMessage>()
        var success = true
        var errorMessage: String? = null

        try {
            // 시나리오 실행
            val executionResult = playwrightExecutor.executeScenario(message)

            // 실행 결과 처리
            stepResults.addAll(executionResult.stepResults)
            success = executionResult.success
            errorMessage = executionResult.errorMessage

        } catch (e: Exception) {
            logger.debug("Failed to execute scenario: ${e.message}", e)
            success = false
            errorMessage = "Unexpected error: ${e.message}"
        }

        // 결과 메시지 생성
        val resultMessage = ScenarioResultMessage(
            executionId = message.executionId,
            scenarioId = message.scenarioId,
            status = if (success) "SUCCESS" else "FAILED",
            errorMessage = errorMessage,
            executionDurationMs = System.currentTimeMillis() - startTime,
            completedAt = LocalDateTime.now(),
            stepResults = stepResults
        )

        // 결과 메시지 전송
        rabbitTemplate.convertAndSend(exchangeName, resultRoutingKey, resultMessage)

        logger.info("Completed scenario execution ${message.executionId} with status: ${resultMessage.status}")
    }
}
