package com.sympsel.service;

import com.sympsel.entitys.Notice;
import com.sympsel.repository.NoticeRepository;
import com.sympsel.utils.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NoticeService {
    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional
    public Notice publish(String publisherUuid, String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("公告标题不能为空");
        }
        long now = System.currentTimeMillis();
        Notice notice = new Notice();
        notice.setUuid(UuidUtil.generate());
        notice.setPublisherUuid(publisherUuid);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setPublishTime(now);
        notice.setUpdateTime(now);
        return noticeRepository.save(notice);
    }

    @Transactional(readOnly = true)
    public Optional<Notice> findByUuid(String uuid) {
        return noticeRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public List<Notice> findAll() {
        return noticeRepository.findAllByOrderByPublishTimeDesc();
    }

    @Transactional(readOnly = true)
    public List<Notice> findByPublisher(String publisherUuid) {
        return noticeRepository.findByPublisherUuid(publisherUuid);
    }

    @Transactional
    public Notice update(String uuid, String title, String content) {
        Notice notice = noticeRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在: " + uuid));
        if (title != null && !title.isBlank()) {
            notice.setTitle(title);
        }
        if (content != null) {
            notice.setContent(content);
        }
        notice.setUpdateTime(System.currentTimeMillis());
        return noticeRepository.save(notice);
    }
}
