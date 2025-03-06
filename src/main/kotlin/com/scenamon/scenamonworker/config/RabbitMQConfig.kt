package com.scenamon.scenamonworker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQConfig {

    @Value("\${rabbitmq.exchange.name}")
    private lateinit var exchangeName: String

    @Value("\${rabbitmq.queue.scenario}")
    private lateinit var scenarioQueueName: String

    @Value("\${rabbitmq.queue.result}")
    private lateinit var resultQueueName: String

    @Value("\${rabbitmq.routing.scenario}")
    private lateinit var scenarioRoutingKey: String

    @Value("\${rabbitmq.routing.result}")
    private lateinit var resultRoutingKey: String

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())
    }

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): Jackson2JsonMessageConverter {
        return Jackson2JsonMessageConverter(objectMapper)
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        jsonMessageConverter: Jackson2JsonMessageConverter
    ): RabbitTemplate {
        val rabbitTemplate = RabbitTemplate(connectionFactory)
        rabbitTemplate.messageConverter = jsonMessageConverter
        return rabbitTemplate
    }

    @Bean
    fun topicExchange(): TopicExchange {
        return TopicExchange(exchangeName)
    }

    @Bean
    fun scenarioQueue(): Queue {
        return Queue(scenarioQueueName, true)
    }

    @Bean
    fun resultQueue(): Queue {
        return Queue(resultQueueName, true)
    }

    @Bean
    fun scenarioBinding(scenarioQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(scenarioQueue)
            .to(topicExchange)
            .with(scenarioRoutingKey)
    }

    @Bean
    fun resultBinding(resultQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(resultQueue)
            .to(topicExchange)
            .with(resultRoutingKey)
    }
}