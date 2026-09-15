package com.sympsel.service;

import com.sympsel.entitys.Town;
import com.sympsel.repository.TownRepository;
import com.sympsel.utils.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TownService {
    private final TownRepository townRepository;

    public TownService(TownRepository townRepository) {
        this.townRepository = townRepository;
    }

    @Transactional
    public Town create(String name, String ownerUuid, String description, String parentTownUuid) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("小镇名称不能为空");
        }
        long now = System.currentTimeMillis();
        Town town = new Town();
        town.setUuid(UuidUtil.generate());
        town.setName(name);
        town.setOwnerUuid(ownerUuid);
        town.setDescription(description);
        town.setParentTownUuid(parentTownUuid);
        town.setCreateTime(now);
        town.setUpdateTime(now);
        if (ownerUuid != null) {
            town.getMemberUuids().add(ownerUuid);
        }
        Town saved = townRepository.save(town);

        if (parentTownUuid != null) {
            townRepository.findById(parentTownUuid).ifPresent(parent -> {
                parent.getChildTownUuids().add(saved.getUuid());
                parent.setUpdateTime(System.currentTimeMillis());
                townRepository.save(parent);
            });
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Town> findByUuid(String uuid) {
        return townRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public List<Town> findAll() {
        return townRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Town> findByOwner(String ownerUuid) {
        return townRepository.findByOwnerUuid(ownerUuid);
    }

    @Transactional(readOnly = true)
    public List<Town> findChildren(String parentTownUuid) {
        return townRepository.findByParentTownUuid(parentTownUuid);
    }

    @Transactional
    public Town addMember(String townUuid, String userUuid) {
        Town town = townRepository.findById(townUuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + townUuid));
        town.getMemberUuids().add(userUuid);
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }

    @Transactional
    public Town removeMember(String townUuid, String userUuid) {
        Town town = townRepository.findById(townUuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + townUuid));
        town.getMemberUuids().remove(userUuid);
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }
}
