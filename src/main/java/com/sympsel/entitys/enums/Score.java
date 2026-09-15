package com.sympsel.entitys.enums;

import lombok.Getter;

@Getter
public enum Score {
    Perfect(5), Good(4), Average(3), Bad(2), Terrible(1);

    private final int value;

    Score(int value) {
        this.value = value;
    }

}
