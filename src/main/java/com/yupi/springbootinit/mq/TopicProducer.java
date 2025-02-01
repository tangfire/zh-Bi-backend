package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Scanner;

public class TopicProducer {
	// 定义交换机名称为"direct-exchange"
    private static final String EXCHANGE_NAME = "topic-exchange";

    public static void main(String[] argv) throws Exception {
        // 创建连接工厂对象
        ConnectionFactory factory = new ConnectionFactory();
        // 设置消息队列的主机地址为本地主机
        factory.setHost("localhost");


        // 设置端口号（Docker映射的端口）
        factory.setPort(5674);

        // 设置虚拟主机
        factory.setVirtualHost("my_vhost");

        // 设置用户名和密码
        factory.setUsername("admin");
        factory.setPassword("8888.216");

        // 创建连接和通道，并确保资源自动关闭
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
        	// 声明使用主题交换机，并指定交换机名称和类型
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        	// 创建一个扫描器对象，从控制台读取用户输入
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                // 读取用户输入的一行文本
                String userInput = scanner.nextLine();
                // 使用空格分隔用户输入的文本
                String[] strings = userInput.split(" ");
                // 如果分隔后的文本长度小于2，则跳过当前循环
                if (strings.length < 1) {
                    continue;
                }
                // 获取用户输入的消息
                String message = strings[0];
                // 获取用户输入的路由键
                String routingKey = strings[1];
            	// 发布消息到指定的交换机和路由键
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
                // 打印消息发送的提示信息，包含消息内容和路由键
                System.out.println(" [x] Sent '" + message + " with routing:" + routingKey + "'");
            }
        }
    }
    //..
}
