package com.yupi.springbootinit.manager;

import com.yupi.springbootinit.MainApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2024/9/12
 * @description
 */
@SpringBootTest(classes = MainApplication.class)
public class TestJava {
    @Resource
    private AIManager aiManager;


    String predefinedInformation = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
            "分析需求：\n" +
            "{数据分析的需求或者目标}\n" +
            "原始数据：\n" +
            "{csv格式的原始数据，用,作为分隔符}\n" +
            "请根据这两部分内容，严格按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）同时不要使用这个符号 '】'\n" +
            "'【【【【【'\n" +
            "{前端 Echarts V5 的 option 配置对象 JSON 代码, 不要生成任何多余的内容，比如注释和代码块标记}\n" +
            "'【【【【【'\n" +
            "{明确的数据分析结论、越详细越好，不要生成多余的注释} \n"
            + "下面是一个具体的例子的模板："
            + "'【【【【【'\n"
            + "{\"xxx\": }"
            + "'【【【【【'\n" +
            "结论：";
    
    // import org.junit.jupiter.api.Test;
    @Test
    public void test() {
        String c = "分析需求：\n" +
                "分析网站用户的增长情况 \n" +
                "请使用 柱状图 \n" +
                "原始数据：\n" +
                "日期,用户数\n" +
                "1号,10\n" +
                " 2号,20\n" +
                " 3号,30";
        String s = aiManager.sendMsgToXingHuo(true, c);
        System.out.println("s = " + s);
    }
    
}

