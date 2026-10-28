package com.xiaoying.studio.simulation;

import com.xiaoying.studio.config.AppProperties;
import com.xiaoying.studio.task.TaskStage;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** 运行时可调整的模拟配置，初始值来自 app.simulation.*（环境变量 SIM_STEP_DELAY_MS / SIM_FAIL_STAGE）。 */
@Component
public class SimulationSettings {

    private final AtomicReference<SimulationConfig> current;

    public SimulationSettings(AppProperties props) {
        AppProperties.Simulation sim = props.simulation();
        this.current = new AtomicReference<>(new SimulationConfig(Math.max(0, sim.stepDelayMs()),
                parseStage(sim.failStage())));
    }

    public SimulationConfig snapshot() {
        return current.get();
    }

    public SimulationConfig update(SimulationConfig next) {
        current.set(next);
        return next;
    }

    public static TaskStage parseStage(String raw) {
        if (raw == null || raw.isBlank() || "NONE".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return TaskStage.valueOf(raw.trim().toUpperCase());
    }
}
