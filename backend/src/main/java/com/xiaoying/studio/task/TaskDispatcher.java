package com.xiaoying.studio.task;

import com.xiaoying.studio.simulation.SimulationConfig;
import com.xiaoying.studio.template.TemplateMapper;
import com.xiaoying.studio.template.TemplateRow;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/** 把已入库的任务投递到后台线程池执行。执行所需数据在工作线程里从数据库重新加载。 */
@Component
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    private final TaskExecutor executor;
    private final TaskPipeline pipeline;
    private final TaskMapper taskMapper;
    private final TemplateMapper templateMapper;
    private final TaskProgressRecorder recorder;

    public TaskDispatcher(@Qualifier("taskWorkerExecutor") TaskExecutor executor, TaskPipeline pipeline,
                          TaskMapper taskMapper, TemplateMapper templateMapper, TaskProgressRecorder recorder) {
        this.executor = executor;
        this.pipeline = pipeline;
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.recorder = recorder;
    }

    public void dispatch(long taskId, SimulationConfig sim) {
        try {
            executor.execute(() -> runSafely(taskId, sim));
        } catch (RejectedExecutionException e) {
            log.warn("worker queue full, reject task {}", taskId);
            recorder.markFailed(taskId, null, "系统繁忙，任务队列已满，请稍后重试");
        }
    }

    private void runSafely(long taskId, SimulationConfig sim) {
        try {
            TaskRow task = taskMapper.findById(taskId);
            if (task == null || TaskStatus.valueOf(task.status()).isTerminal()) {
                return;
            }
            TemplateRow template = templateMapper.findById(task.templateId());
            List<TaskInputRow> inputs = taskMapper.findInputs(List.of(taskId));
            pipeline.run(new TaskContext(taskId, template, inputs), sim);
        } catch (RuntimeException e) {
            log.error("task {} crashed outside pipeline", taskId, e);
            recorder.markFailed(taskId, null, "任务执行异常");
        }
    }
}
