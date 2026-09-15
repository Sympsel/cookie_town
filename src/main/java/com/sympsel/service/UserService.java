package com.sympsel.service;

import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.repository.UserRepository;
import com.sympsel.utils.UuidUtil;
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

    public boolean verifyPassword(String rawPassword, User user) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}