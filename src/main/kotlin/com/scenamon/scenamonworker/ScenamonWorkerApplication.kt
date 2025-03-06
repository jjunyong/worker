package com.scenamon.scenamonworker

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ScenamonWorkerApplication

private val logger = LoggerFactory.getLogger(ScenamonWorkerApplication::class.java)

fun main(args: Array<String>) {
    logger.info("Starting Playwright Worker Application")
    runApplication<ScenamonWorkerApplication>(*args)
}