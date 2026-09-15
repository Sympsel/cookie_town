package com.sympsel.entitys;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@Data
@EqualsAndHashCode(of = "uuid")
public class Comment {
    private String uuid;
    private String publisherUuid;
    private String parentUuid;
    private ArrayList<String> replyUuids;
    private String content;
    private long createTime;
    private long updateTime;
}
