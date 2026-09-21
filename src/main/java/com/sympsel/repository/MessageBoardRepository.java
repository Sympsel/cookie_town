package com.sympsel.repository;

import com.sympsel.entitys.MessageBoard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageBoardRepository extends JpaRepository<MessageBoard, String> {
    List<MessageBoard> findByPublisherUuid(String publisherUuid);

    List<MessageBoard> findAllByOrderByCreateTimeDesc();

    Page<MessageBoard> findByTownUuid(String townUuid, Pageable pageable);
}