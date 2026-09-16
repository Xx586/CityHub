package com.cityhub.upload;

import com.cityhub.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;

    @DeleteMapping("/blog")
    public ApiResponse<Void> deleteBlogImage(@RequestParam String fileName) {
        uploadService.deleteBlogImage(fileName);
        return ApiResponse.ok(null);
    }
}
