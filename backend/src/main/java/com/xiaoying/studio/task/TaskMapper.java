package com.xiaoying.studio.task;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务表读写。所有状态推进都是带前置条件的 UPDATE（WHERE status IN ...），
 * 返回受影响行数，保证终态（SUCCEEDED/FAILED）不会被后续写入改回处理中。
 */
@Mapper
public interface TaskMapper {

    String SELECT_TASK = """
            SELECT t.id, t.user_id, t.template_id, tp.title AS template_title, tp.cover_url AS template_cover_url,
                   t.status, t.stage, t.failed_stage, t.error_message, t.result_url,
                   t.created_at, t.updated_at, t.started_at, t.finished_at
            FROM task t JOIN template tp ON tp.id = t.template_id
            """;

    @Insert("INSERT INTO task (user_id, template_id, status) VALUES (#{userId}, #{templateId}, 'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NewTask task);

    @Insert("INSERT INTO task_asset (task_id, slot_index, asset_id) VALUES (#{taskId}, #{slot}, #{assetId})")
    int insertInput(@Param("taskId") long taskId, @Param("slot") int slot, @Param("assetId") long assetId);

    @Select(SELECT_TASK + " WHERE t.id = #{id}")
    TaskRow findById(long id);

    @Select(SELECT_TASK + " WHERE t.id = #{id} AND t.user_id = #{userId}")
    TaskRow findOwned(@Param("id") long id, @Param("userId") long userId);

    @Select("<script>" + SELECT_TASK + """
             WHERE t.user_id = #{userId}
            <if test="templateId != null"> AND t.template_id = #{templateId}</if>
            <if test="activeOnly"> AND t.status IN ('PENDING', 'PROCESSING')</if>
            ORDER BY t.id DESC LIMIT #{limit}
            </script>
            """)
    List<TaskRow> listOwned(@Param("userId") long userId, @Param("templateId") String templateId,
                            @Param("activeOnly") boolean activeOnly, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT ta.task_id, ta.slot_index, a.id AS asset_id, a.url, a.mime_type, a.size_bytes, a.storage_path
            FROM task_asset ta JOIN asset a ON a.id = ta.asset_id
            WHERE ta.task_id IN
            <foreach collection="taskIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY ta.task_id, ta.slot_index
            </script>
            """)
    List<TaskInputRow> findInputs(@Param("taskIds") Collection<Long> taskIds);

    /** 进入某个阶段。只有未结束的任务可以推进。 */
    @Update("""
            UPDATE task SET status = 'PROCESSING', stage = #{stage}, updated_at = NOW(3),
                   started_at = COALESCE(started_at, NOW(3))
            WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')
            """)
    int markStage(@Param("id") long id, @Param("stage") String stage);

    @Update("""
            UPDATE task SET status = 'SUCCEEDED', result_url = #{resultUrl}, updated_at = NOW(3), finished_at = NOW(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("id") long id, @Param("resultUrl") String resultUrl);

    @Update("""
            UPDATE task SET status = 'FAILED', failed_stage = #{stage}, error_message = #{message},
                   updated_at = NOW(3), finished_at = NOW(3)
            WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING')
            """)
    int markFailed(@Param("id") long id, @Param("stage") String stage, @Param("message") String message);

    /** 启动时把上次进程遗留的未完成任务标记为失败（不做断点续跑）。 */
    @Update("""
            UPDATE task SET status = 'FAILED', failed_stage = stage, error_message = #{message},
                   updated_at = NOW(3), finished_at = NOW(3)
            WHERE status IN ('PENDING', 'PROCESSING')
            """)
    int failUnfinished(@Param("message") String message);

    @Insert("INSERT INTO task_step (task_id, stage, status, started_at) VALUES (#{taskId}, #{stage}, 'RUNNING', NOW(3))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStep(NewTaskStep step);

    @Update("UPDATE task_step SET status = #{status}, message = #{message}, finished_at = NOW(3) WHERE id = #{id}")
    int finishStep(@Param("id") long id, @Param("status") String status, @Param("message") String message);

    @Select("""
            SELECT id, task_id, stage, status, message, started_at, finished_at
            FROM task_step WHERE task_id = #{taskId} ORDER BY id
            """)
    List<TaskStepRow> findSteps(long taskId);
}
