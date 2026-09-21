package com.sympsel.service;

import com.sympsel.dto.LandmarkResponse;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;
import com.sympsel.entitys.enums.Score;
import com.sympsel.entitys.metadatas.Coordinate;
import com.sympsel.repository.CommentRepository;
import com.sympsel.repository.LandmarkRepository;
import com.sympsel.repository.UserRepository;
import com.sympsel.security.PermissionGuard;
import com.sympsel.utils.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LandmarkService {
    private final LandmarkRepository landmarkRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public LandmarkService(LandmarkRepository landmarkRepository, UserRepository userRepository, CommentRepository commentRepository) {
        this.landmarkRepository = landmarkRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Landmark create(String submitterUuid, String name, LandmarkType type, String description, List<String> pictures) {
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
        if (pictures != null) {
            pictures.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .forEach(landmark.getPictures()::add);
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
    public Page<Landmark> findAll(Pageable pageable) {
        return landmarkRepository.findAll(pageable);
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
    public Landmark removeBuilder(String uuid, String builderUuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.getBuilderUuids().remove(builderUuid);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark removeCoordinate(String uuid, Coordinate coordinate) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.getCoordinates().remove(coordinate);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    @Transactional
    public Landmark removePicture(String uuid, String pictureUrl) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        landmark.getPictures().remove(pictureUrl);
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
        PermissionGuard.requireOwnerOrAdmin(landmark.getSubmitterUuid());
        if (landmark.getParentUuid() != null) {
            landmarkRepository.findById(landmark.getParentUuid()).ifPresent(parent -> {
                parent.getChildUuids().remove(uuid);
                parent.setUpdateTime(System.currentTimeMillis());
                landmarkRepository.save(parent);
            });
        }
        for (String cid : List.copyOf(landmark.getCommentUuids())) {
            commentRepository.deleteAll(commentRepository.findByParentUuidOrderByCreateTimeAsc(cid));
            commentRepository.deleteById(cid);
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

    @Transactional
    public Landmark addCommentUuid(String uuid, String commentUuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        if (!landmark.getCommentUuids().contains(commentUuid)) {
            landmark.getCommentUuids().add(commentUuid);
        }
        // 评论可能携带评分：重算地标总评分（仅统计有评分的评论）
        recomputeScore(landmark);
        landmark.setUpdateTime(System.currentTimeMillis());
        return landmarkRepository.save(landmark);
    }

    /**
     * 重算地标总评分：取其挂载评论中「携带评分」的那些，按 Score 的数值求平均，保留 1 位小数。
     * 评论不一定携带评分——无评分的评论不计入；若没有任何带评分的评论，总评分为 0.0。
     */
    private void recomputeScore(Landmark landmark) {
        List<String> commentUuids = List.copyOf(landmark.getCommentUuids());
        if (commentUuids.isEmpty()) {
            landmark.setScore(0.0);
            return;
        }
        double avg = commentRepository.findAllById(commentUuids).stream()
                .map(Comment::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Score::getValue)
                .average()
                .orElse(0.0);
        landmark.setScore(Math.round(avg * 10.0) / 10.0);
    }

    @Transactional(readOnly = true)
    public List<Comment> findComments(String uuid) {
        Landmark landmark = landmarkRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("地标不存在: " + uuid));
        List<String> commentUuids = List.copyOf(landmark.getCommentUuids());
        if (commentUuids.isEmpty()) {
            return List.of();
        }
        Map<String, Comment> commentMap = commentRepository.findAllById(commentUuids).stream()
                .collect(Collectors.toMap(Comment::getUuid, Function.identity()));
        return commentUuids.stream()
                .map(commentMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LandmarkResponse> findAllDto(Pageable pageable) {
        Page<Landmark> page = landmarkRepository.findAll(pageable);
        Map<String, String> names = resolveNames(page.getContent());
        return page.map(l -> LandmarkResponse.from(l, names.get(l.getSubmitterUuid()), builderNamesOf(l, names)));
    }

    /** 详情组装（事务内） */
    @Transactional(readOnly = true)
    public Optional<LandmarkResponse> findDtoByUuid(String uuid) {
        return landmarkRepository.findById(uuid).map(l -> {
            Map<String, String> names = resolveNames(List.of(l));
            return LandmarkResponse.from(l, names.get(l.getSubmitterUuid()), builderNamesOf(l, names));
        });
    }

    private Map<String, String> resolveNames(List<Landmark> list) {
        List<String> ids = list.stream()
                .flatMap(l -> Stream.concat(Stream.of(l.getSubmitterUuid()), l.getBuilderUuids().stream()))
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllByUuidIn(ids).stream()
                .collect(Collectors.toMap(User::getUuid, User::getName));
    }

    private List<String> builderNamesOf(Landmark l, Map<String, String> names) {
        return l.getBuilderUuids().stream().map(names::get).filter(Objects::nonNull).toList();
    }
}
