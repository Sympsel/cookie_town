package com.sympsel.config;

import com.sympsel.entitys.enums.Permission;
import com.sympsel.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动引导：若配置了 app.bootstrap-admin.username 且该用户已注册，则将其提升为 Admin。
 * 解决"所有注册用户默认 Visitor、无第一个管理员"的冷启动问题。幂等，可重复执行。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final String bootstrapAdminUsername;

    public DataInitializer(UserService userService,
                           @Value("${app.bootstrap-admin.username:}") String bootstrapAdminUsername) {
        this.userService = userService;
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapAdminUsername == null || bootstrapAdminUsername.isBlank()) {
            return;
        }
        userService.findByName(bootstrapAdminUsername).ifPresentOrElse(
                user -> {
                    if (user.getPermission() != Permission.Admin) {
                        userService.updatePermission(user.getUuid(), Permission.Admin);
                        log.info("引导管理员：已将用户 [{}] 提升为 Admin", bootstrapAdminUsername);
                    }
                },
                () -> log.warn("引导管理员：用户名 [{}] 不存在，跳过提权（请先注册该用户）", bootstrapAdminUsername)
        );
    }
}