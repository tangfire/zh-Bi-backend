package com.yupi.springbootinit.config;

import com.yupi.springbootinit.constant.BiMqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 交换机名称
    public static final String EXCHANGE_NAME = "code_exchange";

    // 队列名称
    public static final String QUEUE_NAME = "code_queue";

    // 路由键
    public static final String ROUTING_KEY = "my_routingKey";

    // 声明交换机
    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    // 声明队列
    @Bean
    public Queue codeQueue() {
        return QueueBuilder
                .durable(QUEUE_NAME)  // 持久化队列
                .build();
    }

    // 绑定队列到交换机
    @Bean
    public Binding bindingCodeQueue(Queue codeQueue, DirectExchange codeExchange) {
        return BindingBuilder
                .bind(codeQueue)
                .to(codeExchange)
                .with(ROUTING_KEY);
    }

    // 定义队列
    @Bean
    public Queue biQueue() {
        return new Queue(BiMqConstant.BI_QUEUE_NAME, true);
    }

    // 定义交换机
    @Bean
    public DirectExchange biExchange() {
        return new DirectExchange(BiMqConstant.BI_EXCHANGE_NAME);
    }

    // 定义绑定关系
    @Bean
    public Binding biBinding(Queue biQueue, DirectExchange biExchange) {
        return BindingBuilder.bind(biQueue)
            .to(biExchange)
            .with(BiMqConstant.BI_ROUTING_KEY);
    }
}