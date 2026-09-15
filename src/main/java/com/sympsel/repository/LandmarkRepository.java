package com.sympsel.repository;

import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandmarkRepository extends JpaRepository<Landmark, String> {
    List<Landmark> findBySubmitterUuid(String submitterUuid);

    List<Landmark> findByParentUuid(String parentUuid);

    List<Landmark> findByType(LandmarkType type);

    List<Landmark> findByStatus(LandmarkStatus status);
}