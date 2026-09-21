package com.sympsel.repository;

import com.sympsel.entitys.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByPublisherUuid(String publisherUuid);

    List<Comment> findByParentUuid(String parentUuid);

    Page<Comment> findByParentUuidIsNull(Pageable pageable);

    List<Comment> findByParentUuidOrderByCreateTimeAsc(String parentUuid);
}