package com.xiaoying.studio.asset;

import com.xiaoying.studio.auth.AuthInterceptor;
import com.xiaoying.studio.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/api/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetService.AssetView upload(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                                         @RequestParam("templateId") String templateId,
                                         @RequestParam("file") MultipartFile file) {
        return assetService.upload(user, templateId, file);
    }
}
