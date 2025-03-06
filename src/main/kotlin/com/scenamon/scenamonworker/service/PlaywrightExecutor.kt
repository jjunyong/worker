package com.scenamon.scenamonworker.service

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.scenamon.scenamonworker.dto.ExecutionResult
import com.scenamon.scenamonworker.dto.ScenarioExecutionMessage
import com.scenamon.scenamonworker.dto.ScenarioStepMessage
import com.scenamon.scenamonworker.dto.StepAssertionMessage
import com.scenamon.scenamonworker.dto.StepResultMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories

@Service
class PlaywrightExecutor {
    private val logger = LoggerFactory.getLogger(PlaywrightExecutor::class.java)

    @Value("\${playwright.screenshot.dir}")
    private lateinit var screenshotBaseDir: String

    @Value("\${playwright.browser.headless}")
    private var headless: Boolean = true

    /**
     * 시나리오 실행
     */
    fun executeScenario(scenario: ScenarioExecutionMessage): ExecutionResult {
        val stepResults = mutableListOf<StepResultMessage>()
        var success = true
        var errorMessage: String? = null

        try {
            Playwright.create().use { playwright ->
                val launchOptions = BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setSlowMo(50.0)

                playwright.chromium().launch(launchOptions).use { browser ->
                    browser.newContext().use { context ->
                        context.newPage().use { page ->
                            // 시나리오 스텝 실행
                            for (step in scenario.steps) {
                                val stepStartTime = System.currentTimeMillis()
                                var stepSuccess = true
                                var stepError: String? = null
                                var screenshotPath: String? = null

                                try {
                                    logger.info("Executing step ${step.stepOrder}: ${step.description}")
                                    executeStep(page, step)

                                    // 스텝에 정의된 assertion 확인
                                    for (assertion in step.assertions) {
                                        checkAssertion(page, assertion)
                                    }

                                    // 대기 시간이 있으면 대기
                                    if (step.waitAfterInMillis > 0) {
                                        Thread.sleep(step.waitAfterInMillis)
                                    }
                                } catch (e: Exception) {
                                    logger.debug("Step ${step.stepOrder} failed: ${e.message}", e)
                                    stepSuccess = false
                                    stepError = e.message

                                    // 스크린샷 저장
                                    try {
                                        screenshotPath = takeScreenshot(page, scenario, step)
                                    } catch (screenshotError: Exception) {
                                        logger.error("Failed to take screenshot: ${screenshotError.message}", screenshotError)
                                    }

                                    // 검증 단계 실패 시 전체 시나리오 실패로 처리
                                    if (step.isVerificationStep) {
                                        success = false
                                        errorMessage = "Verification step failed: ${e.message}"
                                        break
                                    }
                                }

                                // 스텝 결과 저장
                                stepResults.add(
                                    StepResultMessage(
                                        stepOrder = step.stepOrder,
                                        stepId = step.id,
                                        status = if (stepSuccess) "SUCCESS" else "FAILED",
                                        errorMessage = stepError,
                                        executionDurationMs = System.currentTimeMillis() - stepStartTime,
                                        screenshotPath = screenshotPath
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to execute scenario: ${e.message}", e)
            success = false
            errorMessage = "Failed to execute scenario: ${e.message}"
        }

        return ExecutionResult(
            success = success,
            errorMessage = errorMessage,
            stepResults = stepResults
        )
    }

    /**
     * 스텝 실행
     */
    private fun executeStep(page: Page, step: ScenarioStepMessage) {
        when (step.action) {
            "NAVIGATE" -> page.navigate(step.selector)
            "CLICK" -> page.click(step.selector)
            "TYPE" -> page.fill(step.selector, step.value ?: "")
            "SELECT" -> page.selectOption(step.selector, step.value)
            "HOVER" -> page.hover(step.selector)
            "SCROLL" -> page.evaluate("window.scrollBy(0, ${step.value ?: 500})")
            "WAIT_FOR_ELEMENT" -> page.waitForSelector(step.selector)
            "WAIT_FOR_NAVIGATION" -> {
                page.waitForNavigation { page.click(step.selector) }
            }
            "WAIT_FOR_LOAD_STATE" -> {
                val state = try {
                    LoadState.valueOf(step.value ?: "LOAD")
                } catch (e: Exception) {
                    LoadState.LOAD
                }
                page.waitForLoadState(state)
            }
            "WAIT_FOR_REQUEST" -> {
                page.waitForRequest(step.selector) { page.click(step.value ?: "") }
            }
            "WAIT_FOR_RESPONSE" -> {
                page.waitForResponse(step.selector) { page.click(step.value ?: "") }
            }
            "ASSERT" -> {
                // Assertion은 별도 함수로 처리
                if (step.assertions.isEmpty()) {
                    throw IllegalStateException("Assert action requires at least one assertion")
                }
            }
            "CHECK" -> page.check(step.selector)
            "UNCHECK" -> page.uncheck(step.selector)
            "PRESS" -> page.press(step.selector, step.value ?: "")
            "SCREENSHOT" -> takeScreenshot(page, null, step)
            "API_REQUEST" -> throw UnsupportedOperationException("API_REQUEST action not implemented yet")
            else -> throw IllegalArgumentException("Unknown action: ${step.action}")
        }
    }

    /**
     * 어설션 체크
     */
    private fun checkAssertion(page: Page, assertion: StepAssertionMessage) {
        when (assertion.assertionType) {
            "VISIBLE" -> {
                val element = page.querySelector(assertion.selector)
                if (element == null || !element.isVisible) {
                    throw AssertionError("Element should be visible: ${assertion.selector}")
                }
            }
            "NOT_VISIBLE" -> {
                val element = page.querySelector(assertion.selector)
                if (element != null && element.isVisible) {
                    throw AssertionError("Element should not be visible: ${assertion.selector}")
                }
            }
            "TEXT_CONTAINS" -> {
                val text = page.textContent(assertion.selector) ?: ""
                if (!text.contains(assertion.expectedValue ?: "")) {
                    throw AssertionError("Text should contain '${assertion.expectedValue}': $text")
                }
            }
            "TEXT_EQUALS" -> {
                val text = page.textContent(assertion.selector) ?: ""
                if (text != assertion.expectedValue) {
                    throw AssertionError("Text should equal '${assertion.expectedValue}': $text")
                }
            }
            "TEXT_NOT_CONTAINS" -> {
                val text = page.textContent(assertion.selector) ?: ""
                if (text.contains(assertion.expectedValue ?: "")) {
                    throw AssertionError("Text should not contain '${assertion.expectedValue}': $text")
                }
            }
            "TEXT_NOT_EQUALS" -> {
                val text = page.textContent(assertion.selector) ?: ""
                if (text == assertion.expectedValue) {
                    throw AssertionError("Text should not equal '${assertion.expectedValue}': $text")
                }
            }
            "HAS_ATTRIBUTE" -> {
                val hasAttr = page.evaluate("""
                    document.querySelector("${assertion.selector}")
                        .hasAttribute("${assertion.expectedValue}")
                """.trimIndent()) as Boolean
                if (!hasAttr) {
                    throw AssertionError("Element should have attribute '${assertion.expectedValue}'")
                }
            }
            "NOT_HAS_ATTRIBUTE" -> {
                val hasAttr = page.evaluate("""
                    document.querySelector("${assertion.selector}")
                        .hasAttribute("${assertion.expectedValue}")
                """.trimIndent()) as Boolean
                if (hasAttr) {
                    throw AssertionError("Element should not have attribute '${assertion.expectedValue}'")
                }
            }
            "ATTRIBUTE_EQUALS" -> {
                val attrParts = assertion.expectedValue?.split("=", limit = 2) ?: listOf("", "")
                if (attrParts.size != 2) {
                    throw IllegalArgumentException("Expected value should be in format 'attr=value'")
                }
                val attrName = attrParts[0]
                val attrValue = attrParts[1]

                val actualValue = page.getAttribute(assertion.selector, attrName)
                if (actualValue != attrValue) {
                    throw AssertionError("Attribute '$attrName' should equal '$attrValue': $actualValue")
                }
            }
            "ATTRIBUTE_CONTAINS" -> {
                val attrParts = assertion.expectedValue?.split("=", limit = 2) ?: listOf("", "")
                if (attrParts.size != 2) {
                    throw IllegalArgumentException("Expected value should be in format 'attr=value'")
                }
                val attrName = attrParts[0]
                val attrValue = attrParts[1]

                val actualValue = page.getAttribute(assertion.selector, attrName)
                if (actualValue == null || !actualValue.contains(attrValue)) {
                    throw AssertionError("Attribute '$attrName' should contain '$attrValue': $actualValue")
                }
            }
            "URL_CONTAINS" -> {
                val url = page.url()
                if (!url.contains(assertion.expectedValue ?: "")) {
                    throw AssertionError("URL should contain '${assertion.expectedValue}': $url")
                }
            }
            "URL_EQUALS" -> {
                val url = page.url()
                if (url != assertion.expectedValue) {
                    throw AssertionError("URL should equal '${assertion.expectedValue}': $url")
                }
            }
            "STATUS_CODE_EQUALS" -> {
                // 이 경우는 API 응답 확인 등에 사용됨
                throw UnsupportedOperationException("STATUS_CODE_EQUALS assertion requires API context")
            }
            else -> throw IllegalArgumentException("Unknown assertion type: ${assertion.assertionType}")
        }
    }

    /**
     * 스크린샷 저장
     */
    private fun takeScreenshot(
        page: Page,
        scenario: ScenarioExecutionMessage?,
        step: ScenarioStepMessage
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val projectId = scenario?.projectId?.toString() ?: "unknown"
        val subsystemId = scenario?.subsystemId?.toString() ?: "unknown"
        val scenarioId = scenario?.scenarioId?.toString() ?: "unknown"

        val dirPath = Paths.get("$screenshotBaseDir/$projectId/$subsystemId/$scenarioId")
        dirPath.createDirectories()

        val fileName = "step_${step.stepOrder}_$timestamp.png"
        val fullPath = dirPath.resolve(fileName).toString()

        page.screenshot(Page.ScreenshotOptions().setPath(Path.of(fullPath)))

        return "$projectId/$subsystemId/$scenarioId/$fileName"
    }
}