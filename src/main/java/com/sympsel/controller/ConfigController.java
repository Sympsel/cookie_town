package com.sympsel.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 前端配置端点：暴露特权标签白名单，供前端做 UI 门控（后端鉴权始终是权威）。
 * GET 请求公开（无需认证），返回的仅为标签名，不含敏感信息。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final List<String> privilegedTags;

    public ConfigController(@Value("${app.privileged-tags:}") String privilegedTagsRaw) {
        this.privilegedTags = Arrays.stream(privilegedTagsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @GetMapping("/privileged-tags")
    public List<String> privilegedTags() {
        return privilegedTags;
    }
}
