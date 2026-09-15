package com.sympsel.entitys;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "uuid")
public class Notice {
    private String uuid;
    private String publisherUuid;
    private String title;
    private String content;
    private long publishTime;
    private long updateTime;
}
