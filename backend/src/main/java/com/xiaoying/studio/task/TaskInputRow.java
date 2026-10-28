package com.xiaoying.studio.task;

public record TaskInputRow(Long taskId, Integer slotIndex, Long assetId, String url, String mimeType,
                           Long sizeBytes, String storagePath) {
}
