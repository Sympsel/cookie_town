package com.sympsel.entitys;

import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import com.sympsel.entitys.metadatas.Coordinate;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "landmarks")
public class Landmark {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(length = 36)
    private String submitterUuid;

    @Column(length = 36)
    private String parentUuid;

    @ElementCollection
    @OrderColumn(name = "child_order")
    @CollectionTable(name = "landmark_children", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "child_uuid", length = 36)
    private List<String> childUuids = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "landmark_builders", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "builder_uuid", length = 36)
    @OrderColumn(name = "builder_order")
    private List<String> builderUuids = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "landmark_coordinates", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @OrderColumn(name = "coordinate_order")
    private List<Coordinate> coordinates = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "landmark_pictures", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "picture_url")
    @OrderColumn(name = "picture_order")
    private List<String> pictures = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "landmark_comments", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "comment_uuid", length = 36)
    @OrderColumn(name = "comment_order")
    private List<String> commentUuids = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LandmarkType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LandmarkStatus status;

    private long createTime;
    private long updateTime;
    private double score;
}