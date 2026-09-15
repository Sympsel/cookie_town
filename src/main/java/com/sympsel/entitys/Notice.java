package com.sympsel.entitys;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "uuid")
@Entity
@Table(name = "notices")
public class Notice {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String uuid;

    @Column(length = 36)
    private String publisherUuid;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private long publishTime;
    private long updateTime;
}