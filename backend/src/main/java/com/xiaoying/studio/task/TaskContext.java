package com.xiaoying.studio.task;

import com.xiaoying.studio.template.TemplateRow;
import java.nio.file.Path;
import java.util.List;

/** 一次流水线执行的上下文，阶段之间通过它传递中间产物。 */
public class TaskContext {

    private final long taskId;
    private final TemplateRow template;
    private final List<TaskInputRow> inputs;
    private Path generatedFile;
    private String resultUrl;

    public TaskContext(long taskId, TemplateRow template, List<TaskInputRow> inputs) {
        this.taskId = taskId;
        this.template = template;
        this.inputs = List.copyOf(inputs);
    }

    public long taskId() {
        return taskId;
    }

    public TemplateRow template() {
        return template;
    }

    public List<TaskInputRow> inputs() {
        return inputs;
    }

    public Path generatedFile() {
        return generatedFile;
    }

    public void setGeneratedFile(Path generatedFile) {
        this.generatedFile = generatedFile;
    }

    public String resultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }
}
