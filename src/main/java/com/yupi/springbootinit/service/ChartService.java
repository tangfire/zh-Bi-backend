package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.springbootinit.model.dto.chart.ChartQueryRequest;
import com.yupi.springbootinit.model.dto.chart.ChartRegenRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.vo.BiResponse;

import javax.servlet.http.HttpServletRequest;

/**
* @author Admin
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2025-01-27 18:56:36
*/
public interface ChartService extends IService<Chart> {

    /**
     * MQ 异步重新生成图表（需要更改图表参数）
     *
     * @param chartRegenRequest
     * @param request
     * @return
     */
    BiResponse regenChartByAsyncMq(ChartRegenRequest chartRegenRequest, HttpServletRequest request);

    /**
     * 根据查询
     *
     * @param chartQueryRequest
     * @return
     */
    QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);

    /**
     * 查询结论
     * @param ChartId
     * @return
     */
    Chart selectChartResult(Long ChartId);


}
