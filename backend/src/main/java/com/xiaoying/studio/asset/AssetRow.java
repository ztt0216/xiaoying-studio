package com.xiaoying.studio.asset;

import java.time.Instant;

public record AssetRow(Long id, Long userId, String templateId, String originalName, String mimeType,
                       Long sizeBytes, String storagePath, String url, Instant createdAt) {
}
