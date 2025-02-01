package com.yupi.springbootinit.service;

import javax.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 用户服务测试
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@SpringBootTest
public class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    void userRegister() {
        String userAccount = "yupi";
        String userPassword = "";
        String checkPassword = "123456";
        String userName = "鱼毛";
        String userAvatar = "https://pic4.zhimg.com/v2-188fe7f052e183bbb8370280f99ab38c_r.jpg?source=1940ef5c";
        try {
            long result = userService.userRegister(userAccount, userPassword, checkPassword, userName, userAvatar);
            Assertions.assertEquals(-1, result);
            userAccount = "yu";
            result = userService.userRegister(userAccount, userPassword, checkPassword,userName, userAvatar);
            Assertions.assertEquals(-1, result);
        } catch (Exception e) {

        }
    }
}
