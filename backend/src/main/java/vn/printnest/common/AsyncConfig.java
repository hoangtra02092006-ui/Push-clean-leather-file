package vn.printnest.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bo thuc thi cho cac job dan khuon chay nen.
 *
 * <p>Thuat toan ngon CPU (vai giay cho don lon) nen khong the chay trong luong xu ly HTTP:
 * client se bi treo cho va de timeout. Thay vao do controller tra ve ngay {@code jobId},
 * cong viec that chay o pool nay, frontend hoi trang thai dinh ky.
 */
@Configuration
public class AsyncConfig {

    /** Ten bean duoc tham chieu trong {@code @Async("nestingExecutor")}. */
    public static final String NESTING_EXECUTOR = "nestingExecutor";

    @Bean(NESTING_EXECUTOR)
    public Executor nestingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(2, cores / 2));
        executor.setMaxPoolSize(Math.max(4, cores));
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nesting-");
        executor.initialize();
        return executor;
    }
}
