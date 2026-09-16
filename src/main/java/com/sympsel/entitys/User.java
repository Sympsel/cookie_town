package com.sympsel.entitys;

import com.sympsel.entitys.enums.Permission;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(nullable = false, length = 16)
    private String name;

    @ToString.Exclude
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Permission permission;

    @Column(nullable = false)
    private long createTime;

    @ElementCollection
    @CollectionTable(name = "user_tags", joinColumns = @JoinColumn(name = "user_uuid"))
    @Column(name = "tag")
    @OrderColumn(name = "tag_order")
    private List<String> tags = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String introduction;
}