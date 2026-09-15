package com.sympsel.entitys;

import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import com.sympsel.entitys.metadatas.Coordinate;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
    @CollectionTable(name = "landmark_children", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "child_uuid", length = 36)
    private Set<String> childUuids;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "landmark_builders", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "builder_uuid", length = 36)
    private Set<String> builderUuids;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "landmark_coordinates", joinColumns = @JoinColumn(name = "landmark_uuid"))
    private List<Coordinate> coordinates;

    @ElementCollection
    @CollectionTable(name = "landmark_pictures", joinColumns = @JoinColumn(name = "landmark_uuid"))
    @Column(name = "picture_url")
    private List<String> pictures;

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