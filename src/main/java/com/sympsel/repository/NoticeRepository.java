package com.sympsel.repository;

import com.sympsel.entitys.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, String> {
    List<Notice> findByPublisherUuid(String publisherUuid);

    List<Notice> findAllByOrderByPublishTimeDesc();
}