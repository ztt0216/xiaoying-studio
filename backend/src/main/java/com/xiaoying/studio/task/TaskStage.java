package com.xiaoying.studio.task;

/** 后台按声明顺序执行的三个阶段。 */
public enum TaskStage {
    ASSET_CHECK("素材检查"),
    GENERATE("模拟生成"),
    FINALIZE("结果整理");

    private final String label;

    TaskStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
