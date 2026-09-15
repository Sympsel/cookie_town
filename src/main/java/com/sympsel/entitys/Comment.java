package com.sympsel.entitys;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(length = 36)
    private String publisherUuid;

    @Column(length = 36)
    private String parentUuid;

    @ElementCollection
    @CollectionTable(name = "comment_replies", joinColumns = @JoinColumn(name = "comment_uuid"))
    @Column(name = "reply_uuid", length = 36)
    private Set<String> replyUuids = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String content;

    private long createTime;
    private long updateTime;
}