package com.sympsel.config;

import com.sympsel.security.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final PermissionInterceptor permissionInterceptor;

    public WebConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }

    /**
     * 将项目根目录的 wwwroot/ 作为前端静态资源目录对外服务。
     * 路径相对于应用工作目录（开发期由项目根启动，故 file:wwwroot/ 即项目根的 wwwroot）。
     * 控制器映射（/api/**）优先级高于静态资源处理器，二者不冲突。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:wwwroot/");
    }

    /**
     * 欢迎页：把根路径 "/" 转发到 /index.html。
     * 自定义静态资源处理器不会自动提供 index.html 欢迎页，故需显式映射；
     * 否则访问 "/" 会落到资源处理器因找不到资源而抛 NoResourceFoundException。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
