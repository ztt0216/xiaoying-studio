package com.xiaoying.studio.task;

import com.xiaoying.studio.asset.AssetMapper;
import com.xiaoying.studio.asset.AssetRow;
import com.xiaoying.studio.auth.AuthUser;
import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.simulation.SimulationConfig;
import com.xiaoying.studio.simulation.SimulationSettings;
import com.xiaoying.studio.template.TemplateMapper;
import com.xiaoying.studio.template.TemplateRow;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TaskService {

    static final int MAX_LIST = 100;

    private final TaskMapper taskMapper;
    private final TemplateMapper templateMapper;
    private final AssetMapper assetMapper;
    private final TaskDispatcher dispatcher;
    private final SimulationSettings simulation;

    public TaskService(TaskMapper taskMapper, TemplateMapper templateMapper, AssetMapper assetMapper,
                       TaskDispatcher dispatcher, SimulationSettings simulation) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.assetMapper = assetMapper;
        this.dispatcher = dispatcher;
        this.simulation = simulation;
    }

    /**
     * 校验参数并创建 PENDING 任务，事务提交后才投递到后台，避免工作线程读到未提交的数据。
     * 接口立即返回任务，不等待制作完成。
     */
    @Transactional
    public TaskView create(AuthUser user, String templateId, List<Long> assetIds) {
        TemplateRow template = templateMapper.findById(templateId);
        if (template == null) {
            throw ApiException.notFound("模板不存在");
        }
        if (assetIds == null || assetIds.size() != template.requiredImageCount()) {
            throw ApiException.badRequest("ASSET_COUNT_MISMATCH",
                    "该模板需要 " + template.requiredImageCount() + " 张图片");
        }
        if (new HashSet<>(assetIds).size() != assetIds.size()) {
            throw ApiException.badRequest("DUPLICATE_ASSET", "同一素材不能重复使用");
        }
        Map<Long, AssetRow> owned = assetMapper.findOwned(user.id(), assetIds).stream()
                .collect(Collectors.toMap(AssetRow::id, Function.identity()));
        for (Long id : assetIds) {
            AssetRow asset = owned.get(id);
            if (asset == null) {
                throw ApiException.badRequest("ASSET_NOT_FOUND", "素材不存在或不属于当前用户");
            }
            if (!template.acceptedMimeTypeList().contains(asset.mimeType())) {
                throw ApiException.badRequest("UNSUPPORTED_TYPE", "素材类型不符合模板要求");
            }
            if (asset.sizeBytes() > template.maxFileSizeBytes()) {
                throw ApiException.badRequest("FILE_TOO_LARGE", "素材超过模板大小限制");
            }
        }

        NewTask task = new NewTask();
        task.userId = user.id();
        task.templateId = templateId;
        taskMapper.insert(task);
        for (int i = 0; i < assetIds.size(); i++) {
            taskMapper.insertInput(task.id, i, assetIds.get(i));
        }

        long taskId = task.id;
        SimulationConfig sim = simulation.snapshot();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatch(taskId, sim);
            }
        });
        TaskRow row = taskMapper.findById(taskId);
        return TaskView.of(row, assetIds.stream().map(id -> owned.get(id).url()).toList(), List.of());
    }

    public TaskView get(AuthUser user, long id) {
        TaskRow row = taskMapper.findOwned(id, user.id());
        if (row == null) {
            throw ApiException.notFound("任务不存在");
        }
        List<String> inputs = taskMapper.findInputs(List.of(id)).stream().map(TaskInputRow::url).toList();
        return TaskView.of(row, inputs, taskMapper.findSteps(id));
    }

    public List<TaskView> list(AuthUser user, String templateId, Integer limit) {
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, MAX_LIST));
        List<TaskRow> rows = taskMapper.listOwned(user.id(), templateId, size);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> inputs = taskMapper.findInputs(rows.stream().map(TaskRow::id).toList()).stream()
                .collect(Collectors.groupingBy(TaskInputRow::taskId,
                        Collectors.mapping(TaskInputRow::url, Collectors.toList())));
        return rows.stream().map(r -> TaskView.of(r, inputs.getOrDefault(r.id(), List.of()), null)).toList();
    }
}
