package com.xiaoying.studio.asset;

import com.xiaoying.studio.auth.AuthUser;
import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.template.TemplateMapper;
import com.xiaoying.studio.template.TemplateRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {

    public record AssetView(Long id, String url, String mimeType, long sizeBytes, String originalName) {
    }

    private final TemplateMapper templateMapper;
    private final AssetMapper assetMapper;
    private final FileStorage storage;
    private final FileUrlSigner signer;

    public AssetService(TemplateMapper templateMapper, AssetMapper assetMapper, FileStorage storage,
                        FileUrlSigner signer) {
        this.templateMapper = templateMapper;
        this.assetMapper = assetMapper;
        this.storage = storage;
        this.signer = signer;
    }

    /** 按模板要求校验（大小、魔数识别出的真实类型）后落盘并入库。 */
    public AssetView upload(AuthUser user, String templateId, MultipartFile file) {
        TemplateRow template = templateMapper.findById(templateId);
        if (template == null) {
            throw ApiException.notFound("模板不存在");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "文件为空");
        }
        if (file.getSize() > template.maxFileSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "文件超过模板限制 " + (template.maxFileSizeBytes() / 1024 / 1024) + "MB");
        }
        try {
            byte[] head;
            try (InputStream in = file.getInputStream()) {
                head = in.readNBytes(16);
            }
            Optional<String> mime = ImageSniffer.detect(head);
            if (mime.isEmpty() || !template.acceptedMimeTypeList().contains(mime.get())) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE",
                        "不支持的图片类型，仅支持 " + String.join(", ", template.acceptedMimeTypeList()));
            }
            String relative;
            try (InputStream in = file.getInputStream()) {
                relative = storage.saveUpload(in, ImageSniffer.extensionFor(mime.get()));
            }
            NewAsset asset = new NewAsset();
            asset.userId = user.id();
            asset.templateId = templateId;
            asset.originalName = truncate(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename(), 255);
            asset.mimeType = mime.get();
            asset.sizeBytes = file.getSize();
            asset.storagePath = relative;
            asset.url = FileStorage.urlOf(relative);
            assetMapper.insert(asset);
            return new AssetView(asset.id, signer.sign(asset.url), asset.mimeType, asset.sizeBytes, asset.originalName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
