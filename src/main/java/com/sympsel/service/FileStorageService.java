package com.sympsel.service;

import com.sympsel.utils.UuidUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * 本地文件存储服务：把上传的图片保存到 {app.upload.dir}/{relativeDir}/{uuid}.{ext}，
 * 返回可公开访问的 URL 路径 /uploads/{relativeDir}/{uuid}.{ext}。
 * 将来替换为 FastDFS / 对象存储时，只需改写本类，Controller 与前端无需变动。
 */
@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB，与 multipart 配置保持一致
    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String relativeDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("只允许上传图片文件");
        }

        Path targetDir = root.resolve(relativeDir).normalize();
        if (!targetDir.startsWith(root)) {
            throw new IllegalArgumentException("非法的存储路径");
        }

        String ext = extensionOf(file.getOriginalFilename(), contentType);
        String filename = UuidUtil.generate() + "." + ext;
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("图片保存失败，请稍后重试");
        }
        String relativeUrl = root.relativize(target).toString().replace('\\', '/');
        return "/uploads/" + relativeUrl;
    }

    private String extensionOf(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ALLOWED_EXT.contains(ext)) {
                    return ext;
                }
            }
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) return "png";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("webp")) return "webp";
        return "jpg";
    }
}
