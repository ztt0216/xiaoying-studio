package com.xiaoying.studio.template;

import java.util.Arrays;
import java.util.List;

public record TemplateRow(String id, String title, String category, String coverUrl, String description,
                          Integer requiredImageCount, String acceptedMimeTypes, Long maxFileSizeBytes) {

    public List<String> acceptedMimeTypeList() {
        return Arrays.stream(acceptedMimeTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public TemplateView toView() {
        return new TemplateView(id, title, category, coverUrl, description, requiredImageCount,
                acceptedMimeTypeList(), maxFileSizeBytes);
    }
}
