package com.mini.nio;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Component
public class NioAsyncExecutor {
    // 创建一个独立的EventLoopGroup来处理业务任务。
    // 线程数建议根据任务类型（I/O密集型或CPU密集型）调整。
    private final EventLoopGroup businessEventLoopGroup =
            new NioEventLoopGroup(1);

    public Future<?> submit(Runnable task) {
        return businessEventLoopGroup.next().submit(task);
    }

    public <T> Future<T> submit(Callable<T> callable) {
        return businessEventLoopGroup.next().submit(callable);
    }

    /**
     * 优雅关闭，在应用退出时调用
     */
    @PreDestroy
    public void shutdown() {
        businessEventLoopGroup.shutdownGracefully();
    }
}
