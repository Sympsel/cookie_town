package com.sympsel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 在 Spring 容器刷新（Bean 创建）之前，从外部 config.json 读取 mysql / clean-and-launch / jwt-secret，
 * 注入为 spring.datasource.* 、spring.jpa.hibernate.ddl-auto 、jwt.secret 属性，
 * 使数据库与 JWT 启动不再依赖 DB_USERNAME / DB_PASSWORD / JWT_SECRET 环境变量。
 * <p>运行时机：EnvironmentPostProcessor 在 application.yml 加载之后、DataSource 自动配置之前执行，
 * 因此这里能读到 app.config-dir，注入的属性又以 addFirst（最高优先级）覆盖 yml 中的兜底值。
 * <p>注册：META-INF/spring.factories，键为 org.springframework.boot.EnvironmentPostProcessor
 * （Spring Boot 4.0 起接口由 org.springframework.boot.env 迁至 org.springframework.boot，旧包已弃用）。
 */
public class DataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DataSourceEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "configJsonDataSource";
    private static final String URL_TEMPLATE =
            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configDir = environment.getProperty("app.config-dir", "./config");
        Path path = Paths.get(configDir, "config.json");
        File file = path.toFile();
        if (!file.exists()) {
            throw new IllegalStateException("未找到配置文件 " + path.toAbsolutePath()
                    + "；请确认 app.config-dir（或环境变量 CONFIG_DIR）指向包含 config.json 的目录");
        }

        AppConfig config;
        try {
            config = new ObjectMapper().readValue(file, AppConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析配置文件 " + path.toAbsolutePath() + " 失败：" + e.getMessage(), e);
        }

        AppConfig.Mysql mysql = config.getMysql();
        if (mysql == null || isBlank(mysql.getHost()) || isBlank(mysql.getDatabase())) {
            throw new IllegalStateException("config.json 缺少有效的 mysql 配置块（host / database 不能为空）");
        }

        String url = String.format(URL_TEMPLATE,
                mysql.getHost().trim(), mysql.getPort(), mysql.getDatabase().trim());
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", url);
        props.put("spring.datasource.username", mysql.getUser());
        props.put("spring.datasource.password", mysql.getPassword());
        props.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        props.put("spring.jpa.hibernate.ddl-auto", config.isCleanAndLaunch() ? "create" : "update");
        if (!isBlank(config.getJwtSecret())) {
            props.put("jwt.secret", config.getJwtSecret().trim());
        }

        if (config.getMaxImageSize() > 0) {
            props.put("spring.servlet.multipart.max-file-size", config.getMaxImageSize() + "MB");
        }
        if (config.getMaxRequestSize() > 0) {
            props.put("spring.servlet.multipart.max-request-size", config.getMaxRequestSize() + "MB");
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        log.info("已从 {} 注入数据源：{}@{}:{}/{}（clean-and-launch={}，jwt-secret={}，图片上限={}MB/请求{}MB）",
                path.toAbsolutePath(), mysql.getUser(), mysql.getHost(), mysql.getPort(),
                mysql.getDatabase(), config.isCleanAndLaunch(),
                isBlank(config.getJwtSecret()) ? "未配置(回退环境变量)" : "已配置",
                config.getMaxImageSize(), config.getMaxRequestSize());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public int getOrder() {
        // 必须晚于 ConfigDataEnvironmentPostProcessor（加载 application.yml），才能读到 app.config-dir
        return Ordered.LOWEST_PRECEDENCE;
    }
}