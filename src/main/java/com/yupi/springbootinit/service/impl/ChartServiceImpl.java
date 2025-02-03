package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.rholder.retry.Retryer;
import com.yupi.springbootinit.bizmq.BiMessageProducer;
import com.yupi.springbootinit.bizmq.MQMessage;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.RedisConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.ChartQueryRequest;
import com.yupi.springbootinit.model.dto.chart.ChartRegenRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.TaskStatusEnum;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.RetryException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutionException;

/**
 * @author Admin
 * @description 针对表【chart(图表信息表)】的数据库操作Service实现
 * @createDate 2025-01-27 18:56:36
 */
@Service
@Slf4j
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
        implements ChartService {


    @Resource
    private UserService userService;

    @Resource
    private ChartMapper chartMapper;


    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private BiMessageProducer biMessageProducer;

    @Resource
    private Retryer<Boolean> retryer;


    @Autowired
    private UserServiceImpl userServiceImpl;

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }

        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        // 根据查询条件查询
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


    @Override
    public BiResponse regenChartByAsyncMq(ChartRegenRequest chartRegenRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        // 判断能否生成图表
//        boolean canGenChart = userService.canGenerateChart(loginUser);
//        if (!canGenChart) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "您同时生成图表过多，请稍后再生成");
//        }
//        userService.increaseUserGeneratIngCount(userId);
        // 先校验用户积分是否足够
//        boolean hasScore = userService.userHasScore(loginUser);
//        if (!hasScore) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户积分不足");
//        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 参数校验
        Long chartId = chartRegenRequest.getId();
        String name = chartRegenRequest.getName();
        String goal = chartRegenRequest.getGoal();
        String chartData = chartRegenRequest.getChartData();
        String chartType = chartRegenRequest.getChartType();
        ThrowUtils.throwIf(chartId == null || chartId <= 0, ErrorCode.PARAMS_ERROR, "图表不存在");
        ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "图表名称为空");
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(StringUtils.isBlank(chartData), ErrorCode.PARAMS_ERROR, "原始数据为空");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR, "图表类型为空");
        // 查看重新生成的图标是否存在
        ChartQueryRequest chartQueryRequest = new ChartQueryRequest();
        chartQueryRequest.setId(chartId);
        Long chartCount = chartMapper.selectCount(this.getQueryWrapper(chartQueryRequest));
        if (chartCount <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图表不存在");
        }
        // 限流
        redisLimiterManager.doRateLimit(RedisConstant.REDIS_LIMITER_ID + userId);
        // 更改图表状态为wait
        Chart waitingChart = new Chart();
        BeanUtils.copyProperties(chartRegenRequest, waitingChart);
        waitingChart.setStatus(TaskStatusEnum.WAIT.getStatus());
        boolean updateResult = this.updateById(waitingChart);
        // 将修改后的图表信息保存至数据库
        if (updateResult) {
            log.info("修改后的图表信息初次保存至数据库成功");
            // 初次保存成功，则向MQ投递消息
            trySendMessageByMq(chartId);
            BiResponse biResponse = new BiResponse();
            biResponse.setChartId(chartId);
            return biResponse;
        } else {    // 保存失败则继续重试尝试保存
            try {
                Boolean callResult = retryer.call(() -> {
                    boolean retryResult = this.updateById(waitingChart);
                    if (!retryResult) {
                        log.warn("修改后的图表信息保存至数据库仍然失败，进行重试...");
                    }
                    return !retryResult;
                });
                if (callResult) {
                    trySendMessageByMq(chartId);
                }
                BiResponse biResponse = new BiResponse();
                biResponse.setChartId(chartId);
                return biResponse;
            } catch (RetryException e) {
                // 如果重试了出现异常就要将图表状态更新为failed，并打印日志
                log.error("修改后的图表信息重试保存至数据库失败", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "修改后的图表信息重试保存至数据库失败");
            } catch (ExecutionException | com.github.rholder.retry.RetryException e) {
                log.error("修改后的图表信息重试保存至数据库失败", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "修改后的图表信息重试保存至数据库失败");
            }
        }
    }

    private void trySendMessageByMq(long chartId) {
//        MQMessage mqMessage = MQMessage.builder().chartId(chartId).build();
//        String mqMessageJson = JSONUtil.toJsonStr(mqMessage);
        try {
//            biMessageProducer.sendMessage(mqMessageJson);
            String chartIdStr = String.valueOf(chartId);
            biMessageProducer.sendMessage(chartIdStr);

        } catch (Exception e) {
            log.error("图表成功保存至数据库，但是消息投递失败");
            Chart failedChart = new Chart();
            failedChart.setId(chartId);
            failedChart.setStatus("failed");
            boolean b = this.updateById(failedChart);
            if (!b) {
                throw new RuntimeException("修改图表状态信息为失败失败了");
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ 消息发送失败");
        }
    }


    /**
     * 查询结论
     * @param ChartId
     * @return
     */
    @Override
    public Chart selectChartResult(Long ChartId) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", ChartId);
        Chart chart = chartMapper.selectOne(queryWrapper);


        return chart;
    }
}




