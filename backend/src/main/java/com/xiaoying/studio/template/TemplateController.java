package com.xiaoying.studio.template;

import com.xiaoying.studio.common.ApiException;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {

    public record TemplateListResponse(List<String> categories, List<TemplateView> items) {
    }

    private final TemplateMapper templateMapper;

    public TemplateController(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @GetMapping("/api/templates")
    public TemplateListResponse list(@RequestParam(required = false) String category) {
        String filter = StringUtils.hasText(category) ? category.trim() : null;
        List<TemplateView> items = templateMapper.findAll(filter).stream().map(TemplateRow::toView).toList();
        return new TemplateListResponse(templateMapper.findCategories(), items);
    }

    @GetMapping("/api/templates/{id}")
    public TemplateView get(@PathVariable String id) {
        TemplateRow row = templateMapper.findById(id);
        if (row == null) {
            throw ApiException.notFound("模板不存在");
        }
        return row.toView();
    }
}
