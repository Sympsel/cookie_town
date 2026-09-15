package com.sympsel.repository;

import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByName(String name);

    boolean existsByName(String name);

    List<User> findByPermission(Permission permission);
}