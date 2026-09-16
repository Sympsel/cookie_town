package com.sympsel.service;

import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import com.sympsel.entitys.metadatas.Coordinate;
import com.sympsel.repository.LandmarkRepository;
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
public class LandmarkService {
    private final LandmarkRepository landmarkRepository;
    private final UserRepository userRepository;

    public LandmarkService(LandmarkRepository landmarkRepository, UserRepository userRepository) {
        this.landmarkRepository = landmarkRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Landmark create(String submitterUuid, String name, LandmarkType type, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("地标名称不能为空");
        }
        long now = System.currentTimeMillis();
        Landmark landmark = new Landmark();
        landmark.setUuid(UuidUtil.generate());
        landmark.setSubmitterUuid(submitterUuid);
        landmark.setName(name);
        landmark.setType(type);
        landmark.setDescription(description);
        landmark.setStatus(LandmarkStatus.Normal);
        landmark.setCreateTime(now);
        landmark.setUpdateTime(now);
        if (submitterUuid != null) {
            landmark.getBuilderUuids().add(submitterUuid);
        }
        return landmarkRepository.save(landmark);
    }

    @Transactional(readOnly = true)
    public Optional<Landmark> findByUuid(String uuid) {
        return landmarkRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public List<Landmark> findAll() {
        return landmarkRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Landmark> findBySubmitter(String submitterUuid) {
        return landmarkRepository.findBySubmitterUuid(submitterUuid);
    }

    @Transactional(readOnly = true)
    public List<Landmark> findByType(LandmarkType type) {
        return landmarkRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<Landmark> findByStatus(LandmarkStatus status) {
        return landmarkRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Landmark> findChildren(String parentUuid) {
        return landmarkRepository.findByParentUuid(parentUuid);
    }

    @Transactional
    public Landmark updateStatus(String uuid, LandmarkStatus status) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.setStatus(status);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark addBuilder(String uuid, String builderUuid) {
        if (!UuidUtil.isValid(builderUuid)) {
            throw new IllegalArgumentException("非法的用户 uuid: " + builderUuid);
        }
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        if (!landmark.getBuilderUuids().contains(builderUuid)) {
            landmark.getBuilderUuids().add(builderUuid);
            landmark.setUpdateTime(System.currentTimeMillis());
        }
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark addCoordinate(String uuid, Coordinate coordinate) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.getCoordinates().add(coordinate);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark addPicture(String uuid, String pictureUrl) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.getPictures().add(pictureUrl);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark update(String uuid, String name, LandmarkType type, String description) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        if (name != null && !name.isBlank()) {
            landmark.setName(name);
        }
        if (type != null) {
            landmark.setType(type);
        }
        if (description != null) {
            landmark.setDescription(description);
        }
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public void delete(String uuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        if (landmark.getParentUuid() != null) {
            landmarkRepository.findById(landmark.getParentUuid()).ifPresent(parent -> {
                parent.getChildUuids().remove(uuid);
                parent.setUpdateTime(System.currentTimeMillis());
                landmarkRepository.save(parent);
            });
        }
        landmarkRepository.delete(landmark);
    }

    @Transactional(readOnly = true)
    public List<User> findBuilders(String uuid) {
        Landmark landmark = landmarkRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("地标不存在: " + uuid)
        );
        List<String> builderUuids = List.copyOf(landmark.getBuilderUuids());
        if (builderUuids.isEmpty()) {
            return List.of();
        }
        Map<String, User> userMap = userRepository.findAllByUuidIn(builderUuids).stream()
                .collect(Collectors.toMap(User::getUuid, Function.identity()));
        return builderUuids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Coordinate> findCoordinates(String uuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        return List.copyOf(landmark.getCoordinates());
    }

    @Transactional(readOnly = true)
    public List<String> findPictures(String uuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        return List.copyOf(landmark.getPictures());
    }
}
