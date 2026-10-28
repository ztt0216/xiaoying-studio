package com.xiaoying.studio.asset;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetMapper {

    @Insert("""
            INSERT INTO asset (user_id, template_id, original_name, mime_type, size_bytes, storage_path, url)
            VALUES (#{userId}, #{templateId}, #{originalName}, #{mimeType}, #{sizeBytes}, #{storagePath}, #{url})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NewAsset asset);

    @Select("""
            <script>
            SELECT id, user_id, template_id, original_name, mime_type, size_bytes, storage_path, url, created_at
            FROM asset WHERE user_id = #{userId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<AssetRow> findOwned(@Param("userId") long userId, @Param("ids") Collection<Long> ids);
}
