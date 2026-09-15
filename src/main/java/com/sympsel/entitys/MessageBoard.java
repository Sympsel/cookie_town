package com.sympsel.entitys;

import com.sympsel.entitys.enums.Score;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@Data
@EqualsAndHashCode(of = "uuid")
public class MessageBoard {
    private String uuid;
    private String publisherUuid;
    private String content;
    private Score score;
    private long createTime;
    private long updateTime;
    private ArrayList<String> replyCommentUuids;
}
