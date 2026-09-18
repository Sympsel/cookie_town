package com.sympsel.service;

import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.Town;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.repository.UserRepository;
import com.sympsel.security.ForbiddenException;
import com.sympsel.security.PermissionGuard;
import com.sympsel.security.UserContext;
import com.sympsel.utils.UuidUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // 开发者标签名：拥有该标签的用户视为开发者，可修复其它玩家账户信息
    private final String developerTag;
    private final TownService townService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TownService townService,
                       @Value("${app.developer-tag:开发者}") String developerTag) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.townService = townService;
        this.developerTag = developerTag;
    }

    /**
     * 判断某用户是否拥有任一特权标签（实时查库，标签变更即时生效，无需重新登录）。
     * 调用方必须处于事务内（tags 为懒加载集合）。
     */
    private boolean isDeveloper(String uuid) {
        if (developerTag == null || developerTag.isBlank()) {
            return false;
        }
        return userRepository.findById(uuid)
                .map(user -> user.getTags().contains(developerTag))
                .orElse(false);
    }

    /**
     * 账户信息鉴权：仅「本人」或「拥有特权标签的开发者」可修改目标用户的账户信息。
     * 管理员角色本身不再自动通过——降低 Admin 权力，账户修复统一交由开发者。
     */
    private void requireSelfOrDeveloper(String targetUuid) {
        UserContext.CurrentUser current = UserContext.require();
        if (Objects.equals(current.uuid(), targetUuid)) {
            return;
        }
        if (!isDeveloper(current.uuid())) {
            throw new ForbiddenException("无权操作：修改其它玩家的账户信息需要开发者特权标签");
        }
    }

    @Transactional
    public User register(String name, String rawPassword) {
        if (name == null || name.length() < 3 || name.length() > 16) {
            throw new IllegalArgumentException("用户名长度需为 3-16 位");
        }
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 18) {
            throw new IllegalArgumentException("密码长度需为 6-18 位");
        }
        if (userRepository.existsByName(name)) {
            throw new IllegalArgumentException("用户名已存在: " + name);
        }

        User user = new User();
        user.setUuid(UuidUtil.generate());
        user.setName(name);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPermission(Permission.Visitor);
        user.setCreateTime(System.currentTimeMillis());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUuid(String uuid) {
        return userRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByName(String name) {
        return userRepository.findByName(name);
    }

    /**
     * 按标签查询用户（供启动引导同步开发者名单使用）。
     */
    @Transactional(readOnly = true)
    public List<User> findByTag(String tag) {
        return userRepository.findByTag(tag);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * 用户列表（带身份排序）：
     * 主镇镇长 > 开发者（特权标签） > 普通管理员 > 成员 > 访客 > 黑名单（Marked）；
     * 同档内保持原有的创建时间倒序。
     * 社区量级数据，内存排序 + 手动分页即可；用户量大时再下沉到 SQL。
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> findAllDtoSorted(Pageable pageable) {
        String mayorUuid = townService.findMain().map(Town::getOwnerUuid).orElse(null);
        List<UserResponse> sorted = userRepository.findAll().stream()
                .map(user -> UserResponse.from(user, List.copyOf(user.getTags())))
                .sorted(Comparator
                        .comparingInt((UserResponse r) -> roleRank(r, mayorUuid))
                        .thenComparing(Comparator.comparingLong(UserResponse::createTime).reversed()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = Math.min(from + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    private int roleRank(UserResponse r, String mayorUuid) {
        if (mayorUuid != null && mayorUuid.equals(r.uuid())) return 0; // 主镇镇长
        if (developerTag != null && !developerTag.isBlank()
                && r.tags().contains(developerTag)) return 1;          // 开发者
        return switch (r.permission()) {
            case Admin -> 2;   // 普通管理员
            case Common -> 3;  // 成员
            case Visitor -> 4; // 访客
            case Marked -> 5;  // 黑名单
        };
    }

    /**
     * 分页查询并直接在事务内映射为 UserResponse（含 tags）。
     * tags 是懒加载集合，必须在事务边界内取出，故映射放在 Service 而非 Controller（OSIV 关闭）。
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> findAllDto(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(user -> UserResponse.from(user, List.copyOf(user.getTags())));
    }

    public boolean verifyPassword(String rawPassword, User user) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    @Transactional
    public User update(String uuid, String name, String introduction) {
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + uuid));
        // 仅本人或开发者可修改账户信息（用户名/简介）；管理员角色不再自动通过
        requireSelfOrDeveloper(uuid);
        if (name != null && !name.isBlank()) {
            if (name.length() < 3 || name.length() > 16) {
                throw new IllegalArgumentException("用户名长度需为 3-16 位");
            }
            if (!name.equals(user.getName()) && userRepository.existsByName(name)) {
                throw new IllegalArgumentException("用户名已存在: " + name);
            }
            user.setName(name);
        }
        if (introduction != null) {
            user.setIntroduction(introduction);
        }
        return userRepository.save(user);
    }

    /**
     * 重置指定用户的登录密码：仅本人或开发者可操作（管理员角色不自动通过）。
     * 使开发者无需依赖外界账号绑定（如 QQ 邮箱）即可帮玩家找回账号。
     */
    @Transactional
    public void resetPassword(String uuid, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 18) {
            throw new IllegalArgumentException("密码长度需为 6-18 位");
        }
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + uuid));
        requireSelfOrDeveloper(uuid);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Transactional
    public User updateAvatar(String uuid, String avatarUrl) {
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(uuid);
        user.setAvatar(avatarUrl);
        return userRepository.save(user);
    }

    @Transactional
    public void delete(String uuid) {
        if (!userRepository.existsById(uuid)) {
            throw new IllegalArgumentException("用户不存在: " + uuid);
        }
        userRepository.deleteById(uuid);
    }

    @Transactional(readOnly = true)
    public List<String> findTags(String uuid) {
        User user = userRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("用户不存在: " + uuid)
        );
        return List.copyOf(user.getTags());
    }

    /**
     * 按 UUID 查询并直接在事务内映射为 UserResponse（含 tags），供玩家详情页使用。
     * tags 懒加载，必须在事务边界内取出（OSIV 关闭）。
     */
    @Transactional(readOnly = true)
    public Optional<UserResponse> findDtoByUuid(String uuid) {
        return userRepository.findById(uuid)
                .map(user -> UserResponse.from(user, List.copyOf(user.getTags())));
    }

    /**
     * 管理员为指定用户添加自定义标签：去重、限长，返回更新后的标签列表（事务内取出）。
     * 权限门控由 Controller 的 @RequirePermission(Admin) 负责。
     */
    @Transactional
    public List<String> addTag(String uuid, String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("标签不能为空");
        }
        String trimmed = tag.trim();
        if (trimmed.length() > 16) {
            throw new IllegalArgumentException("标签长度不能超过 16 位");
        }
        User user = userRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("用户不存在: " + uuid)
        );
        if (!user.getTags().contains(trimmed)) {
            user.getTags().add(trimmed);
            userRepository.save(user);
        }
        return List.copyOf(user.getTags());
    }

    /**
     * 管理员移除指定用户的自定义标签，返回更新后的标签列表（事务内取出）。
     */
    @Transactional
    public List<String> removeTag(String uuid, String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("标签不能为空");
        }
        User user = userRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("用户不存在: " + uuid)
        );
        user.getTags().remove(tag.trim());
        userRepository.save(user);
        return List.copyOf(user.getTags());
    }

    @Transactional
    public User updatePermission(String uuid, Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("权限不能为空");
        }
        User user = userRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("用户不存在: " + uuid)
        );
        user.setPermission(permission);
        return userRepository.save(user);
    }

    /**
     * 启动引导：确保指定用户名的账号为 Admin。
     * 账号不存在则用给定用户名 + 密码创建并直接设为 Admin；已存在则仅在非 Admin 时提权（不改密码）。
     * 取代旧的 app.bootstrap-admin 机制，由 config.json 的 first-admin 驱动，解决冷启动无管理员的死锁。
     */
    @Transactional
    public void ensureAdmin(String username, String rawPassword) {
        if (username == null || username.length() < 3 || username.length() > 16) {
            throw new IllegalArgumentException("用户名长度需为 3-16 位");
        }
        Optional<User> existing = userRepository.findByName(username);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getPermission() != Permission.Admin) {
                user.setPermission(Permission.Admin);
                userRepository.save(user);
            }
            return;
        }
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 18) {
            throw new IllegalArgumentException("密码长度需为 6-18 位");
        }
        User user = new User();
        user.setUuid(UuidUtil.generate());
        user.setName(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPermission(Permission.Admin);
        user.setCreateTime(System.currentTimeMillis());
        userRepository.save(user);
    }
}