package com.yupi.springbootinit.manager;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 分布式令牌桶限流服务（基于Redisson实现，支持集群环境）
 *
 * 功能特性：
 * 1. 支持按不同业务维度（用户ID/接口路径等）创建独立限流器[2,6](@ref)
 * 2. 采用令牌桶算法，允许突发流量消耗累积令牌[5](@ref)
 * 3. 基于Redis实现分布式一致性，避免单节点限流失效[4](@ref)
 */
@Service
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 执行分布式限流检查
     *
     * @param key 限流器唯一标识，示例值：
     *            - "api:login:10.0.0.1"        // IP维度限流
     *            - "user:12345:create_order"   // 用户+接口维度限流[3](@ref)
     * @throws BusinessException 当请求被限流时抛出TOO_MANY_REQUEST异常[6](@ref)
     */
    public void doRateLimit(String key) {
        // 获取或创建令牌桶限流器（不存在时自动创建）
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        /*
         * 配置令牌桶规则（仅首次调用生效）
         * 参数说明：
         *   RateType.OVERALL   -> 集群共享令牌桶（所有节点共用限额）[1,5](@ref)
         *   5                  -> 单位时间窗口内令牌生成数量
         *   1                  -> 时间窗口长度（需结合单位参数）
         *   RateIntervalUnit.SECONDS -> 时间单位（秒）
         * 实际规则：每秒生成5个令牌，即允许每秒钟最多5次请求[4](@ref)
         */
        rateLimiter.trySetRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);
        // 尝试获取1个令牌（非阻塞模式）
        boolean canOp = rateLimiter.tryAcquire(1);
        // 触发限流时抛出标准化异常（HTTP 429状态码映射）
        if (!canOp) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
    }
}
