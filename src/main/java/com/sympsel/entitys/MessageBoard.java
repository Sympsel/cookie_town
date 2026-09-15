package com.sympsel.entitys;

import com.sympsel.entitys.enums.Score;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "message_boards")
public class MessageBoard {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(length = 36)
    private String publisherUuid;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Score score;

    private long createTime;
    private long updateTime;

    @ElementCollection
    @CollectionTable(name = "message_board_replies", joinColumns = @JoinColumn(name = "message_board_uuid"))
    @Column(name = "reply_comment_uuid", length = 36)
    private Set<String> replyCommentUuids = new HashSet<>();
}