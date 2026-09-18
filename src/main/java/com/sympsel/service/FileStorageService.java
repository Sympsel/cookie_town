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
    private final Path root;
    private final long maxImageSizeMb;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String dir, @Value("${max-image-size:10}") long maxImageSizeMb) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.maxImageSizeMb = maxImageSizeMb * 1024 * 1024;
    }

    public String store(MultipartFile file, String relativeDir) {
        return store(file, relativeDir, UuidUtil.generate());
    }

    /**
     * 以固定文件名（baseName）保存图片，同名覆盖。用于头像等"每个主体仅一张"的场景，
     * 如 store(file, "avatars", userUuid) -> /uploads/avatars/{userUuid}.{ext}。
     */
    public String store(MultipartFile file, String relativeDir, String baseName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > maxImageSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过 " + maxImageSizeMb + "MB");
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
        Path target = targetDir.resolve(baseName + "." + ext).normalize();
        if (!target.startsWith(targetDir)) {
            throw new IllegalArgumentException("非法的文件名");
        }
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

    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        Path target = root.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 清理旧文件失败不应影响新头像保存
        }
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
