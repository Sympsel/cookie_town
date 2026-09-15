package com.sympsel.entitys;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "towns")
public class Town {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(nullable = false)
    private String name;

    @Column(length = 36)
    private String ownerUuid;

    @Column(length = 36)
    private String parentTownUuid;

    @Column(columnDefinition = "TEXT")
    private String description;

    private long createTime;
    private long updateTime;

    @ElementCollection
    @CollectionTable(name = "town_members", joinColumns = @JoinColumn(name = "town_uuid"))
    @Column(name = "member_uuid", length = 36)
    private Set<String> memberUuids = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "town_children", joinColumns = @JoinColumn(name = "town_uuid"))
    @Column(name = "child_town_uuid", length = 36)
    private Set<String> childTownUuids = new HashSet<>();

    private double score;
}