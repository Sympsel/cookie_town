package com.sympsel.entitys;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

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
    @OrderColumn(name = "member_order")
    @CollectionTable(name = "town_members", joinColumns = @JoinColumn(name = "town_uuid"))
    @Column(name = "member_uuid", length = 36)
    private List<String> memberUuids = new ArrayList<>();

    @ElementCollection
    @OrderColumn(name = "child_order")
    @CollectionTable(name = "town_children", joinColumns = @JoinColumn(name = "town_uuid"))
    @Column(name = "child_town_uuid", length = 36)
    private List<String> childTownUuids = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "town_pictures", joinColumns = @JoinColumn(name = "town_uuid"))
    @Column(name = "picture_url")
    @OrderColumn(name = "picture_order")
    private List<String> pictures = new ArrayList<>();

    private double score;
}