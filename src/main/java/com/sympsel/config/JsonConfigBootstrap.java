package com.sympsel.config;

import com.sympsel.entitys.Town;
import com.sympsel.entitys.User;
import com.sympsel.service.TownService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动引导：把外部 config.json 的配置落地到数据库 / 运行时，取代旧的 DataInitializer（app.bootstrap-admin）。
 * 幂等，可重复执行；任一步骤失败只记录日志、不阻断启动（优雅降级）。
 * <p>职责：
 * <ol>
 *   <li>page-size → 设置 {@link PageUtil} 的默认每页条数；</li>
 *   <li>first-admin → 确保首个管理员账号存在且为 Admin（不存在则用给定密码创建）；</li>
 *   <li>developers → 同步开发者标签：名单内用户打标、名单外持有该标签者撤标。</li>
 * </ol>
 */
@Component
public class JsonConfigBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigBootstrap.class);

    private final UserService userService;
    private final TownService townService;
    private final AppConfig appConfig;
    private final String developerTag;

    public JsonConfigBootstrap(UserService userService,
                               TownService townService,
                               AppConfig appConfig,
                               @Value("${app.developer-tag:开发者}") String developerTag) {
        this.userService = userService;
        this.townService = townService;
        this.appConfig = appConfig;
        this.developerTag = developerTag;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        applyPageSize();
        bootstrapFirstAdmin();
        syncDevelopers();
        syncMainTown();
    }

    private void applyPageSize() {
        PageUtil.setDefaultSize(appConfig.getPageSize());
        log.info("配置生效：page-size={}，develop-mode={}", appConfig.getPageSize(), appConfig.isDevelopMode());
    }

    private void bootstrapFirstAdmin() {
        List<String> firstAdmin = appConfig.getFirstAdmin();
        if (firstAdmin == null || firstAdmin.size() < 2
                || firstAdmin.get(0) == null || firstAdmin.get(0).isBlank()) {
            log.warn("config.json 未配置 first-admin（需 [用户名, 密码]），跳过引导管理员");
            return;
        }
        String username = firstAdmin.get(0).trim();
        String password = firstAdmin.get(1);
        try {
            userService.ensureAdmin(username, password);
            log.info("引导管理员：已确保用户 [{}] 为 Admin", username);
        } catch (Exception e) {
            log.error("引导管理员失败 [{}]：{}", username, e.getMessage());
        }
    }

    private void syncDevelopers() {
        if (developerTag == null || developerTag.isBlank()) {
            log.warn("app.developer-tag 为空，跳过开发者名单同步");
            return;
        }
        List<String> roster = appConfig.getDevelopers();
        Set<String> names = (roster == null ? List.<String>of() : roster).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // 名单内：确保拥有开发者标签
        for (String name : names) {
            try {
                userService.findByName(name).ifPresentOrElse(
                        user -> userService.addTag(user.getUuid(), developerTag),
                        () -> log.warn("开发者名单用户 [{}] 不存在，跳过打标签（请先注册该用户）", name)
                );
            } catch (Exception e) {
                log.error("为 [{}] 打开发者标签失败：{}", name, e.getMessage());
            }
        }

        // 名单外：撤销开发者标签（防止离任开发者残留特权）
        try {
            for (User user : userService.findByTag(developerTag)) {
                if (!names.contains(user.getName())) {
                    userService.removeTag(user.getUuid(), developerTag);
                    log.info("移除用户 [{}] 的开发者标签（不在 config.json 名单内）", user.getName());
                }
            }
        } catch (Exception e) {
            log.error("同步撤销开发者标签失败：{}", e.getMessage());
        }
    }

    private void syncMainTown() {
        AppConfig.MainTown mt = appConfig.getMainTown();
        if (mt == null || mt.getName() == null || mt.getName().isBlank()) {
            log.warn("config.json 未配置 main-town.name，跳过主镇同步");
            return;
        }
        try {
            Town town = townService.upsertMainTown(
                    mt.getName(), mt.getDescription(), mt.getOwnerUsername(), mt.getPictures());
            if (town.getOwnerUuid() == null) {
                log.warn("主镇 [{}] 已同步，但镇长 [{}] 尚未注册，暂为无主镇；其注册后重启将自动回填",
                        town.getName(), mt.getOwnerUsername());
            } else {
                log.info("主镇 [{}] 已同步，镇长 uuid={}", town.getName(), town.getOwnerUuid());
            }
        } catch (Exception e) {
            log.error("主镇同步失败：{}", e.getMessage());
        }
    }
}
