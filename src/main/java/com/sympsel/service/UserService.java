package com.sympsel.service;

import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.repository.UserRepository;
import com.sympsel.security.PermissionGuard;
import com.sympsel.utils.UuidUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
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
        PermissionGuard.requireOwnerOrAdmin(uuid);
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
}