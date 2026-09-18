package com.sympsel.service;

import com.sympsel.entitys.Town;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.repository.TownRepository;
import com.sympsel.repository.UserRepository;
import com.sympsel.security.PermissionGuard;
import com.sympsel.utils.UuidUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    /**
     * 主镇 = 无父镇的根镇。若历史数据存在多个根镇，取创建时间最早者作为主镇。
     */
    @Transactional(readOnly = true)
    public Optional<Town> findMain() {
        return townRepository.findByParentTownUuidIsNull().stream()
                .min(Comparator.comparingLong(Town::getCreateTime));
    }

    /**
     * 幂等创建 / 同步主镇（由 config.json 的 main-town 在启动时驱动）。
     * <p>镇长用户名解析不到（尚未注册）时建为无主镇（ownerUuid=null）并记 warn；
     * 待其注册后下次启动会自动回填 ownerUuid 并加入成员。
     * <p>name/description/pictures 以 config 为准同步（config 为主镇的权威来源）。
     * 懒加载集合（pictures/memberUuids）均在本事务内访问，规避 OSIV 关闭下的异常。
     */
    @Transactional
    public Town upsertMainTown(String name, String description, String ownerUsername, List<String> pictures) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("主镇名称不能为空");
        }
        String ownerUuid = (ownerUsername == null || ownerUsername.isBlank())
                ? null
                : userRepository.findByName(ownerUsername.trim()).map(User::getUuid).orElse(null);
        List<String> pics = (pictures == null) ? List.of() : pictures;
        long now = System.currentTimeMillis();

        Optional<Town> existing = findMain();
        if (existing.isEmpty()) {
            Town town = new Town();
            town.setUuid(UuidUtil.generate());
            town.setName(name.trim());
            town.setDescription(description);
            town.setParentTownUuid(null);
            town.setOwnerUuid(ownerUuid);
            town.setCreateTime(now);
            town.setUpdateTime(now);
            if (ownerUuid != null) {
                town.getMemberUuids().add(ownerUuid);
            }
            town.getPictures().addAll(pics);
            return townRepository.save(town);
        }

        Town town = existing.get();
        town.setName(name.trim());
        if (description != null) {
            town.setDescription(description);
        }
        // 轮播图以 config 为准覆盖同步
        town.getPictures().clear();
        town.getPictures().addAll(pics);
        // 回填镇长：此前无主（或镇长变更）且现在能解析到 uuid
        if (ownerUuid != null && !ownerUuid.equals(town.getOwnerUuid())) {
            town.setOwnerUuid(ownerUuid);
            if (!town.getMemberUuids().contains(ownerUuid)) {
                town.getMemberUuids().add(ownerUuid);
            }
        }
        town.setUpdateTime(now);
        return townRepository.save(town);
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
    public Page<Town> findAll(Pageable pageable) {
        return townRepository.findAll(pageable);
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
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
        if (!town.getMemberUuids().contains(userUuid)) {
            town.getMemberUuids().add(userUuid);
            town.setUpdateTime(System.currentTimeMillis());
        }
        userRepository.findById(userUuid).ifPresent(
                user -> {
                    if (user.getPermission() == Permission.Visitor) {
                        user.setPermission(Permission.Common);
                        userRepository.save(user);
                    }
                }
        );
        return townRepository.save(town);
    }

    @Transactional
    public Town removeMember(String townUuid, String userUuid) {
        Town town = townRepository.findById(townUuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + townUuid));
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
        town.getMemberUuids().remove(userUuid);
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }

    @Transactional
    public Town update(String uuid, String name, String description) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
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
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
        if (town.getParentTownUuid() != null) {
            townRepository.findById(town.getParentTownUuid()).ifPresent(parent -> {
                parent.getChildTownUuids().remove(uuid);
                parent.setUpdateTime(System.currentTimeMillis());
                townRepository.save(parent);
            });
        }
        townRepository.delete(town);
    }

    /**
     * 批量解析 uuid -> 用户名
     * 查不到的用户不会出现在结果中（调用方按 null 处理）。
     */
    @Transactional(readOnly = true)
    public Map<String, String> findNamesByUuids(Collection<String> uuids) {
        List<String> valid = uuids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByUuidIn(valid).stream()
                .collect(Collectors.toMap(User::getUuid, User::getName));
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

    @Transactional
    public Town addPicture(String uuid, String pictureUrl) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
        town.getPictures().add(pictureUrl);
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }

    @Transactional
    public Town removePicture(String uuid, String pictureUrl) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(town.getOwnerUuid());
        town.getPictures().remove(pictureUrl);
        town.setUpdateTime(System.currentTimeMillis());
        return townRepository.save(town);
    }

    @Transactional(readOnly = true)
    public List<String> findPictures(String uuid) {
        Town town = townRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("小镇不存在: " + uuid));
        return List.copyOf(town.getPictures());
    }
}