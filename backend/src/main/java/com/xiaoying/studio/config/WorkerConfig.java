package com.xiaoying.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkerConfig {

    /**
     * 制作任务专用线程池：与 Web 请求线程隔离，提交接口只负责入库和投递。
     * 队列满时抛出 RejectedExecutionException，由 TaskDispatcher 把任务标记为失败。
     */
    @Bean(name = "taskWorkerExecutor")
    public ThreadPoolTaskExecutor taskWorkerExecutor(AppProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, props.worker().threads());
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(props.worker().queueCapacity());
        executor.setThreadNamePrefix("task-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
