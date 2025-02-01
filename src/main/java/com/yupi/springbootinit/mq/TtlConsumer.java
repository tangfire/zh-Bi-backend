package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TtlConsumer {
	// 定义我们正在监听的队列名称"ttl_queue"
    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        // 设置连接工厂的主机地址为 "localhost"
        factory.setHost("localhost");

        // 设置端口号（Docker映射的端口）
        factory.setPort(5674);

        // 设置虚拟主机
        factory.setVirtualHost("my_vhost");

        // 设置用户名和密码
        factory.setUsername("admin");
        factory.setPassword("8888.216");


        // 建立连接
        Connection connection = factory.newConnection();
        // 创建频道
        Channel channel = connection.createChannel();

        // 创建队列，指定消息过期参数
        Map<String, Object> args = new HashMap<String, Object>();
        // 设置消息过期时间为5秒
        args.put("x-message-ttl", 5000);
        // 创建队列，并传入队列名称、是否持久化、是否私有、是否自动删除，args 指定参数
        channel.queueDeclare(QUEUE_NAME, false, false, false, args);
    	// 打印等待消息的提示信息
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // 定义了如何处理消息的回调函数
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        // 消费消息，该方法会持续阻塞，等待接收消息
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }
}
