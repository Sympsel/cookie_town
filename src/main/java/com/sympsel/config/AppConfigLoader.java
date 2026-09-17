package com.sympsel.config;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动时从外部目录加载 config.json（唯一真源）为 {@link AppConfig} Bean。
 * <p>目录由 application.yml 的 {@code app.config-dir}（默认 ./config，可用 CONFIG_DIR 覆盖）指定，
 * 相对于应用工作目录（与 wwwroot/、uploads/ 一致，开发期即项目根）。
 * <p>文件缺失或解析失败时不崩溃，退回默认空配置并记录日志（优雅降级）。
 */
@Configuration
public class AppConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(AppConfigLoader.class);

    @Bean
    public AppConfig appConfig(@Value("${app.config-dir:./config}") String configDir) {
        Path path = Paths.get(configDir, "config.json");
        File file = path.toFile();
        if (!file.exists()) {
            log.warn("未找到配置文件 {}，使用默认配置（developers 空、first-admin 空、page-size=20、develop-mode=false）",
                    path.toAbsolutePath());
            return new AppConfig();
        }
        try {
            AppConfig config = new ObjectMapper().readValue(file, AppConfig.class);
            log.info("已加载配置文件 {}", path.toAbsolutePath());
            return config;
        } catch (Exception e) {
            log.error("解析配置文件 {} 失败，使用默认配置：{}", path.toAbsolutePath(), e.getMessage());
            return new AppConfig();
        }
    }
}
