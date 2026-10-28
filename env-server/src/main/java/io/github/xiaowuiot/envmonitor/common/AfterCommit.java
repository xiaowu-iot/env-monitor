package io.github.xiaowuiot.envmonitor.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交之后再执行。发钉钉、给设备下发命令这类外部动作要放在提交之后：
 * 既不拖长事务，也不会出现"命令发了、数据却回滚了"，对方回得再快也能查到刚写的数据。
 * <p>
 * 注意 afterCommit 回调里原事务的资源还绑在线程上，这时候写库不会提交，所以任务交给后台线程执行。
 * 没有事务时直接在当前线程执行。
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "after-commit");
        t.setDaemon(true);
        return t;
    });

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    EXECUTOR.execute(() -> {
                        try {
                            task.run();
                        } catch (RuntimeException e) {
                            log.error("事务提交后的任务执行失败", e);
                        }
                    });
                }
            });
        } else {
            task.run();
        }
    }
}
