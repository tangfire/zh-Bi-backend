package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BiMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AIManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.enums.TaskStatusEnum;
import com.yupi.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


// 使用@Component注解标记该类为一个组件，让Spring框架能够扫描并将其纳入管理
@Component
// 使用@Slf4j注解生成日志记录器
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AIManager aiManager;

    /**
     * 接收消息的方法
     *
     * @param message      接收到的消息内容，是一个字符串类型
     * @param channel      消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag  消息的投递标签，用于唯一标识一条消息
     */
    // 使用@SneakyThrows注解简化异常处理
    @SneakyThrows
    // 使用@RabbitListener注解指定要监听的队列名称为"code_queue"，并设置消息的确认机制为手动确认
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    // @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,用于从消息头中获取投递标签(deliveryTag),
    // 在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        log.info("receiveMessage message = {}", message);
        if (StringUtils.isBlank(message)) {
            // 如果更新失败，拒绝当前消息，让消息重新进入队列
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            // 如果图表为空，拒绝消息并抛出业务异常
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表为空");
        }


        // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。(为了防止同一个任务被多次执行)
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus(TaskStatusEnum.RUNNING.getStatus());

        boolean b = chartService.updateById(updateChart);
        if (!b) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "图表状态更改失败");
            return;
        }

        // 调用 AI
        String csvData = aiManager.sendMsgToXingHuo(true, buildUserInput(chart));

        String[] splits = csvData.split("结论：");

        if (splits.length < 2) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "AI 生成错误");
            return;

        }

        String genChart = splits[0];
        // 去除 ```json 和最后的 ```
        genChart = genChart.replace("```json", "").replace("```", "").trim();
        String genResult = splits[1];

        Chart updateChartResult = new Chart();
        updateChartResult.setId(chart.getId());
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);

        updateChartResult.setStatus(TaskStatusEnum.SUCCEED.getStatus());

        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            return;

        }

        // 使用日志记录器打印接收到的消息内容
        log.info("receiveMessage message = {}", message);
        // 投递标签是一个数字标识,它在消息消费者接收到消息后用于向RabbitMQ确认消息的处理状态。通过将投递标签传递给channel.basicAck(deliveryTag, false)方法,可以告知RabbitMQ该消息已经成功处理,可以进行确认和从队列中删除。
        // 手动确认消息的接收，向RabbitMQ发送确认消息
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 构造用户输入
     * @param chart
     * @return
     */
    private String buildUserInput(Chart chart){

        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容:\n" +
                "分析需求:\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据:\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容(此外不要输出任何多余的开头、结尾、注释)\n" +
                "{前端 Echarts v5 的 option 配置对象js代码(注意不要写var option = {},直接写{}里面的东西)，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "明确的数据分析结论、越详细越好，不要生成多余的注释";

        // 用户输入
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师,接下来我会给你我的分析目标和原始数据,请告诉我分析结论").append("\n");
        userInput.append(prompt).append("\n");
        userInput.append("分析需求:").append("\n").append(goal).append(",请使用").append(chartType).append("\n");


        userInput.append("原始数据:").append("\n");

        userInput.append(csvData).append("\n");

        return userInput.toString();
    }

    // 上面的接口很多用到异常,直接定义一个工具类
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(TaskStatusEnum.FAILED.getStatus());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

}
