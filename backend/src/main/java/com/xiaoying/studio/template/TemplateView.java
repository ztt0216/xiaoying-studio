package com.xiaoying.studio.template;

import java.util.List;

public record TemplateView(String id, String title, String category, String coverUrl, String description,
                           int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {
}
