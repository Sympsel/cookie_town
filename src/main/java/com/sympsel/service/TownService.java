package com.sympsel.service;

import com.sympsel.entitys.Town;
import com.sympsel.entitys.User;
import com.sympsel.repository.TownRepository;
import com.sympsel.repository.UserRepository;
import com.sympsel.utils.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TownService {
    private final TownRepository townRepository;
    private final UserRepository userRepository;

    public TownService(TownRepository townRepository, UserRepository userRepository) {
        this.townRepository = townRepository;
        this.userRepository = userRepository;
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
        if (!UuidUtil.isValid(userUuid)) {
            throw new IllegalArgumentException("非法的用户 uuid: " + userUuid);
        }
        Town town = townRepository.findById(townUuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + townUuid));
        if (!town.getMemberUuids().contains(userUuid)) {
            town.getMemberUuids().add(userUuid);
            town.setUpdateTime(System.currentTimeMillis());
        }
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

    @Transactional
    public Town update(String uuid, String name, String description) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        if (name != null && !name.isBlank()) {
            town.setName(name);
        }
        if (description != null) {
            town.setDescription(description);
        }
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }

    @Transactional
    public void delete(String uuid) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        if (town.getParentTownUuid() != null) {
            townRepository.findById(town.getParentTownUuid()).ifPresent(parent -> {
                parent.getChildTownUuids().remove(uuid);
                parent.setUpdateTime(System.currentTimeMillis());
                townRepository.save(parent);
            });
        }
        townRepository.delete(town);
    }

    @Transactional(readOnly = true)
    public List<User> findMembers(String uuid) {
        Town town = townRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("小镇不存在: " + uuid)
        );
        List<String> memberUuids = List.copyOf(town.getMemberUuids());
        if (memberUuids.isEmpty()) {
            return List.of();
        }
        Map<String, User> userMap = userRepository.findAllByUuidIn(memberUuids).stream()
                .collect(Collectors.toMap(User::getUuid, Function.identity()));
        return memberUuids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
