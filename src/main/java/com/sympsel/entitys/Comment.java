package com.sympsel.entitys;

import com.sympsel.entitys.enums.Score;
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
    @OrderColumn(name = "reply_order")
    private List<String> replyUuids = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String content;

    // 评分（可选）：评论不一定携带评分；地标评论用它聚合出地标总评分
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Score score;

    private long createTime;
    private long updateTime;
}