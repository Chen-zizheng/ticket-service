package com.czz.ticket.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计一次请求里实际下发的 SQL 条数。
 * 用在测试里守住 N+1：列表接口必须 <= 3 条 SQL，而不是 1 + N 条。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlCounter implements Interceptor {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    public static void reset() {
        COUNTER.set(0);
    }

    public static int count() {
        return COUNTER.get();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        COUNTER.incrementAndGet();
        return invocation.proceed();
    }
}
