package com.sympsel.entitys;

import com.sympsel.entitys.enums.Score;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 36)
    private String townUuid;

    private long createTime;
    private long updateTime;

    @ElementCollection
    @CollectionTable(name = "message_board_replies", joinColumns = @JoinColumn(name = "message_board_uuid"))
    @OrderColumn(name = "reply_order")
    @Column(name = "reply_comment_uuid", length = 36)
    private List<String> replyCommentUuids = new ArrayList<>();
}