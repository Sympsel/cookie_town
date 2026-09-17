package com.sympsel.controller;

import com.sympsel.config.AppConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前端配置端点：暴露开发者标签、开发模式、分页默认值，供前端做 UI 门控与展示（后端鉴权始终是权威）。
 * GET 请求公开（无需认证），返回的均为非敏感配置。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final List<String> privilegedTags;
    private final boolean developMode;
    private final int pageSize;

    public ConfigController(@Value("${app.developer-tag:开发者}") String developerTag, AppConfig appConfig) {
        this.privilegedTags = (developerTag == null || developerTag.isBlank())
                ? List.of()
                : List.of(developerTag);
        this.developMode = appConfig.isDevelopMode();
        this.pageSize = appConfig.getPageSize();
    }

    /** 视为「开发者」的标签列表（当前为单元素），前端据此显隐账户管理入口。 */
    @GetMapping("/privileged-tags")
    public List<String> privilegedTags() {
        return privilegedTags;
    }

    /** 开发模式开关（来自 config.json 的 develop-mode）。 */
    @GetMapping("/develop-mode")
    public boolean developMode() {
        return developMode;
    }

    /** 分页默认每页条数（来自 config.json 的 page-size）。 */
    @GetMapping("/page-size")
    public int pageSize() {
        return pageSize;
    }
}
