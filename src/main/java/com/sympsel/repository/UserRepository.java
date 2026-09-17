package com.sympsel.repository;

import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByName(String name);

    boolean existsByName(String name);

    List<User> findByPermission(Permission permission);

    List<User> findAllByUuidIn(List<String> uuids);

    /** 查询拥有指定标签（tags 元素集合包含该值）的所有用户，供开发者名单同步使用。 */
    @Query("SELECT u FROM User u JOIN u.tags t WHERE t = :tag")
    List<User> findByTag(@Param("tag") String tag);
}