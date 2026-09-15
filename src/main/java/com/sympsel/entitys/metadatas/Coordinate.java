package com.sympsel.entitys.metadatas;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class Coordinate {
    private int x;
    private int y;
    private int z;
    /**
     * @brief 维度
     *
     * -1: 下界
     * 0: 主世界
     * 1: 末地
     */
    private int level;
}