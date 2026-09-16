package com.cityhub.upload;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UploadService {
    private final Path uploadRoot;

    public UploadService(@Value("${cityhub.upload-dir}") String uploadDirectory) {
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public void deleteBlogImage(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        Path target = uploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(uploadRoot) || target.equals(uploadRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        try {
            if (!Files.deleteIfExists(target)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件删除失败");
        }
    }
}
