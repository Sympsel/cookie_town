package com.sympsel.entitys;

import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import com.sympsel.entitys.metadatas.Coordinate;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@Data
@EqualsAndHashCode(of = "uuid")
public class Landmark {
    private String uuid;
    private String submitterUuid;
    private String parentUuid;
    private ArrayList<String> childUuids;
    private String name;
    private ArrayList<String> builderUuids;
    private String description;
    private ArrayList<Coordinate> coordinates;
    private ArrayList<String> pictures;
    private LandmarkType type;
    private LandmarkStatus status;
    private long createTime;
    private long updateTime;
    private double score;
}
