package com.xiaoying.studio.template;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper {

    String COLUMNS = "id, title, category, cover_url, description, required_image_count, accepted_mime_types, max_file_size_bytes";

    @Select("<script>SELECT " + COLUMNS + """
             FROM template
            <where><if test="category != null">category = #{category}</if></where>
            ORDER BY category, sort_order, id
            </script>
            """)
    List<TemplateRow> findAll(@Param("category") String category);

    @Select("SELECT " + COLUMNS + " FROM template WHERE id = #{id}")
    TemplateRow findById(String id);

    @Select("SELECT category FROM template GROUP BY category ORDER BY MIN(sort_order), category")
    List<String> findCategories();
}
