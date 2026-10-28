package com.xiaoying.studio.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 线程池在内存中，进程重启后未完成的任务不会继续执行。
 * 本项目不做断点续跑，启动时把它们明确标记为失败，避免前端永远显示“处理中”。
 * （假设单实例部署；多实例需要租约/心跳，见 README。）
 */
@Component
public class StartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final TaskMapper taskMapper;

    public StartupRecovery(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = taskMapper.failUnfinished("服务重启，任务已中断，请重新提交");
        if (n > 0) {
            log.warn("marked {} unfinished task(s) as FAILED after restart", n);
        }
    }
}
