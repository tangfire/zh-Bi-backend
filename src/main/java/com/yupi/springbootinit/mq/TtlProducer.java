package com.yupi.springbootinit.mq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class TtlProducer {
	// 定义队列名称为"ttl_queue"
    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        // 设置端口号（Docker映射的端口）
        factory.setPort(5674);

        // 设置虚拟主机
        factory.setVirtualHost("my_vhost");

        // 设置用户名和密码
        factory.setUsername("admin");
        factory.setPassword("8888.216");

        // 建立连接、创建频道
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            // 消息虽然可以重复声明,必须指定相同的参数,在消费者的创建队列要指定过期时间,
            // 后面要放args,在生产者你又想重新创建队列，又不指定参数，那肯定会有问题，
            // 所以要把这里的创建队列注释掉。
            // channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            
            // 发送消息
            String message = "Hello World!";

            // 给消息指定过期时间
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    // 设置消息的过期时间为1000毫秒
                    .expiration("10000")
                    .build();
            // 发布消息到指定的交换机（"my-exchange"）和路由键（"routing-key"）
            // 使用指定的属性（过期时间）和消息内容（UTF-8编码的字节数组）
            channel.basicPublish("",QUEUE_NAME , properties, message.getBytes(StandardCharsets.UTF_8));



            // 使用默认的交换机，将消息发送到指定队列
//            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}
