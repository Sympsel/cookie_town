package com.sympsel.repository;

import com.sympsel.entitys.Town;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TownRepository extends JpaRepository<Town, String> {
    List<Town> findByOwnerUuid(String ownerUuid);

    List<Town> findByParentTownUuid(String parentTownUuid);

    List<Town> findByParentTownUuidIsNull();
}