package com.sympsel.entitys;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@Data
@EqualsAndHashCode(of = "uuid")
public class Town {
    private String uuid;
    private String name;
    private String ownerUuid;
    private String parentTownUuid;
    private String description;
    private long createTime;
    private long updateTime;
    private ArrayList<String> memberUuids;
    private ArrayList<String> childTownUuids;
    private double score;
}
