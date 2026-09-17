package com.sympsel.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 config.json 的映射模型（唯一真源，启动时由 {@link AppConfigLoader} 读取）。
 * <p>字段与 JSON 键对应：developers / first-admin / main-town / page-size / develop-mode。
 * JSON 中的 comment-* 等未知键会被忽略（{@link JsonIgnoreProperties}），可当作文内注释使用。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {


    /**
     * MySQL 连接信息（数据源）：启动早期注入 spring.datasource.*，取代 DB_USERNAME/DB_PASSWORD 环境变量。
     */
    private Mysql mysql = new Mysql();

    /**
     * 开发者名单（用户名）：启动时同步开发者标签。
     */
    private List<String> developers = new ArrayList<>();

    /**
     * 第一个管理员 [用户名, 密码]：不存在则创建并设为 Admin，已存在则确保为 Admin。
     */
    @JsonProperty("first-admin")
    private List<String> firstAdmin = new ArrayList<>();

    /**
     * 主镇信息（本轮仅解析，实际建镇/同步留待后续子系统）。
     */
    @JsonProperty("main-town")
    private MainTown mainTown = new MainTown();

    /**
     * 分页默认每页条数。
     */
    @JsonProperty("page-size")
    private int pageSize = 20;

    /**
     * 开发模式开关。
     */
    @JsonProperty("develop-mode")
    private boolean developMode = false;

    /**
     * 清库重建后启动：true → hibernate.ddl-auto=create（每次启动重建表结构），false → update。
     */
    @JsonProperty("clean-and-launch")
    private boolean cleanAndLaunch = false;

    /**
     * JWT 签名密钥（至少 32 字节）：启动早期注入 jwt.secret
     */
    @JsonProperty("jwt-secret")
    private String jwtSecret;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MainTown {
        private String name;
        private String description;
        private String ownerUsername;
        private List<String> pictures = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mysql {
        private String host = "localhost";
        private int port = 3306;
        private String user = "root";
        private String password = "";
        private String database = "cookie_town";
    }
}
