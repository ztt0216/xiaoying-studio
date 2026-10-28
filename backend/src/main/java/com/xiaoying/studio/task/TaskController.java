package com.xiaoying.studio.task;

import com.xiaoying.studio.auth.AuthInterceptor;
import com.xiaoying.studio.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    public record CreateTaskRequest(@NotBlank String templateId, @NotEmpty List<Long> assetIds) {
    }

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 受理即返回（202），制作在后台进行。 */
    @PostMapping("/api/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskView create(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                           @Valid @RequestBody CreateTaskRequest req) {
        return taskService.create(user, req.templateId(), req.assetIds());
    }

    /** active=true 只返回未结束的任务（前端登录后用来恢复全局跟踪）。 */
    @GetMapping("/api/tasks")
    public List<TaskView> list(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user,
                               @RequestParam(required = false) String templateId,
                               @RequestParam(defaultValue = "false") boolean active,
                               @RequestParam(required = false) Integer limit) {
        return taskService.list(user, StringUtils.hasText(templateId) ? templateId : null, active, limit);
    }

    /** 只读查询：不会推进任务状态。 */
    @GetMapping("/api/tasks/{id}")
    public TaskView get(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user, @PathVariable long id) {
        return taskService.get(user, id);
    }
}
