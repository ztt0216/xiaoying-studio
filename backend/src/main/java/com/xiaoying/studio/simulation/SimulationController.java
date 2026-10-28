package com.xiaoying.studio.simulation;

import com.xiaoying.studio.common.ApiException;
import com.xiaoying.studio.task.TaskStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 开发用接口：调整模拟耗时与失败阶段。生产可通过 SIM_DEV_API=false 关闭。 */
@RestController
@Validated
@ConditionalOnProperty(prefix = "app.simulation", name = "dev-api-enabled", havingValue = "true")
public class SimulationController {

    public record SimulationView(long stepDelayMs, String failStage, List<String> stages) {
        static SimulationView of(SimulationConfig c) {
            return new SimulationView(c.stepDelayMs(), c.failStage() == null ? "NONE" : c.failStage().name(),
                    Arrays.stream(TaskStage.values()).map(Enum::name).toList());
        }
    }

    public record SimulationUpdate(@Min(0) @Max(120_000) Long stepDelayMs, String failStage) {
    }

    private final SimulationSettings settings;

    public SimulationController(SimulationSettings settings) {
        this.settings = settings;
    }

    @GetMapping("/api/dev/simulation")
    public SimulationView get() {
        return SimulationView.of(settings.snapshot());
    }

    @PutMapping("/api/dev/simulation")
    public SimulationView update(@RequestBody @jakarta.validation.Valid SimulationUpdate req) {
        SimulationConfig cur = settings.snapshot();
        TaskStage stage;
        try {
            stage = req.failStage() == null ? cur.failStage() : SimulationSettings.parseStage(req.failStage());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STAGE", "failStage 取值为 NONE / ASSET_CHECK / GENERATE / FINALIZE");
        }
        long delay = req.stepDelayMs() == null ? cur.stepDelayMs() : req.stepDelayMs();
        return SimulationView.of(settings.update(new SimulationConfig(delay, stage)));
    }
}
