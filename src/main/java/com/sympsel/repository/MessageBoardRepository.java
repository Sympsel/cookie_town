package com.sympsel.repository;

import com.sympsel.entitys.MessageBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageBoardRepository extends JpaRepository<MessageBoard, String> {
    List<MessageBoard> findByPublisherUuid(String publisherUuid);

    List<MessageBoard> findAllByOrderByCreateTimeDesc();
}