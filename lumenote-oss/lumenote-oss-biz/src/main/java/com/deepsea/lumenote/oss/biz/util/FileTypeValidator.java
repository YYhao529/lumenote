package com.deepsea.lumenote.oss.biz.util;

import com.deepsea.framework.common.exception.BizException;
import com.deepsea.lumenote.oss.biz.enums.ResponseCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件校验（安全）
 *
 * <p>只校验扩展名是不够的：攻击者可以把 .exe 改名成 .png。这里做三层校验：
 * 扩展名白名单 → Content-Type 前缀 → 文件头魔数（magic number）。</p>
 */
@Slf4j
public final class FileTypeValidator {

    private static final long MAX_SIZE = 200L * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "webm");

    private FileTypeValidator() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResponseCodeEnum.FILE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResponseCodeEnum.FILE_TOO_LARGE);
        }

        String originalName = file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!IMAGE_EXTENSIONS.contains(extension) && !VIDEO_EXTENSIONS.contains(extension)) {
            log.warn("拒绝上传：扩展名不允许, name={}, ext={}", originalName, extension);
            throw new BizException(ResponseCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && !contentType.toLowerCase(Locale.ROOT).startsWith("video/")
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            log.warn("拒绝上传：Content-Type 不允许, name={}, contentType={}", originalName, contentType);
            throw new BizException(ResponseCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }

        if (!magicMatches(file, extension)) {
            log.warn("拒绝上传：文件头与扩展名不符, name={}, ext={}", originalName, extension);
            throw new BizException(ResponseCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private static String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 读取文件头判断真实类型
     */
    private static boolean magicMatches(MultipartFile file, String extension) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(head);
        } catch (Exception e) {
            log.warn("读取文件头失败: {}", e.getMessage());
            return false;
        }
        if (read < 4) {
            return false;
        }

        return switch (extension) {
            case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "gif" -> head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
            case "bmp" -> head[0] == 'B' && head[1] == 'M';
            case "webp" -> read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            // mp4 / mov：第 4~7 字节是 "ftyp"
            case "mp4", "mov" -> head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            // webm：EBML 头
            case "webm" -> (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                    && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3;
            default -> false;
        };
    }
}
