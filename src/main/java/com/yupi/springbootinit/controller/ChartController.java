package com.yupi.springbootinit.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.bizmq.BiMessageProducer;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.FileConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.TaskQueueFullException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AIManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.dto.file.UploadFileRequest;
import com.yupi.springbootinit.model.dto.post.PostQueryRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.Post;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.FileUploadBizEnum;
import com.yupi.springbootinit.model.enums.TaskStatusEnum;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AIManager aiManager;

    // 引用
    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    // 自动注入一个线程池的实例
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;



    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }


    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);


        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }


    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);


        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */

    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
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

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);

        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


    /**
     * 智能分析(同步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /**
         * 校验文件
         *
         * 首先,拿到用户请求的文件;
         * 取到原始文件大小
         */
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();


        /**
         * 校验文件大小
         *
         * 定义一个常量表示1MB;
         * 一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /**
         * 校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)
         *
         * 利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx","xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());


        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容:\n" +
                "分析需求:\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据:\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容(此外不要输出任何多余的开头、结尾、注释)\n" +
                "{前端 Echarts v5 的 option 配置对象js代码(注意不要写var option = {},直接写{}里面的东西)，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "明确的数据分析结论、越详细越好，不要生成多余的注释";


        // 用户输入
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师,接下来我会给你我的分析目标和原始数据,请告诉我分析结论").append("\n");
        userInput.append(prompt).append("\n");
        userInput.append("分析需求:").append("\n").append(goal).append(",请使用").append(chartType).append("\n");


        userInput.append("原始数据:").append("\n");
        // 压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(result).append("\n");

        String csvData = aiManager.sendMsgToXingHuo(true, userInput.toString());

        String[] splits = csvData.split("结论：");

        if (splits.length < 2){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
        }

        String genChart = splits[0];
        // 去除 ```json 和最后的 ```
        genChart = genChart.replace("```json", "").replace("```", "").trim();
        String genResult = splits[1];


        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);


    }






    /**
     * 智能分析(异步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /**
         * 校验文件
         *
         * 首先,拿到用户请求的文件;
         * 取到原始文件大小
         */
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();


        /**
         * 校验文件大小
         *
         * 定义一个常量表示1MB;
         * 一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /**
         * 校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)
         *
         * 利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());


        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容:\n" +
                "分析需求:\n" +
                "{数据分析的需求或者目标}\n" +
                "原始数据:\n" +
                "{csv格式的原始数据，用,作为分隔符}\n" +
                "请根据这两部分内容，按照以下指定格式生成内容(此外不要输出任何多余的开头、结尾、注释)\n" +
                "{前端 Echarts v5 的 option 配置对象js代码(注意不要写var option = {},直接写{}里面的东西)，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                "明确的数据分析结论、越详细越好，不要生成多余的注释";


        // 用户输入
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师,接下来我会给你我的分析目标和原始数据,请告诉我分析结论").append("\n");
        userInput.append(prompt).append("\n");
        userInput.append("分析需求:").append("\n").append(goal).append(",请使用").append(chartType).append("\n");


        userInput.append("原始数据:").append("\n");
        // 压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(result).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus(TaskStatusEnum.WAIT.getStatus());

        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        // 提交任务前的检查
        if (threadPoolExecutor.getQueue().remainingCapacity() == 0 &&
                threadPoolExecutor.getActiveCount() >= threadPoolExecutor.getMaximumPoolSize()) {
            throw new TaskQueueFullException("任务队列已满，请稍后再试。");
        }


        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。(为了防止同一个任务被多次执行)
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus(TaskStatusEnum.RUNNING.getStatus());

            boolean b = chartService.updateById(updateChart);
            if (!b) {

                handleChartUpdateError(chart.getId(), "图表状态更改失败");
                return;
            }

            // 调用 AI
            String csvData = aiManager.sendMsgToXingHuo(true, userInput.toString());

            String[] splits = csvData.split("结论：");

            if (splits.length < 2) {
                handleChartUpdateError(chart.getId(), "AI 生成错误");
                return;

            }

            String genChart = splits[0];
            // 去除 ```json 和最后的 ```
            genChart = genChart.replace("```json", "").replace("```", "").trim();
            String genResult = splits[1];

            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);

            updateChartResult.setStatus(TaskStatusEnum.SUCCEED.getStatus());

            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
                return;

            }

        },threadPoolExecutor);


        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);


    }

    // 上面的接口很多用到异常,直接定义一个工具类
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(TaskStatusEnum.FAILED.getStatus());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }



    /**
     * 智能分析(异步消息队列)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /**
         * 校验文件
         *
         * 首先,拿到用户请求的文件;
         * 取到原始文件大小
         */
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();


        /**
         * 校验文件大小
         *
         * 定义一个常量表示1MB;
         * 一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /**
         * 校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)
         *
         * 利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容:\n" +
//                "分析需求:\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据:\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容(此外不要输出任何多余的开头、结尾、注释)\n" +
//                "{前端 Echarts v5 的 option 配置对象js代码(注意不要写var option = {},直接写{}里面的东西)，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "明确的数据分析结论、越详细越好，不要生成多余的注释";
//
//
//        // 用户输入
//        StringBuilder userInput = new StringBuilder();
////        userInput.append("你是一个数据分析师,接下来我会给你我的分析目标和原始数据,请告诉我分析结论").append("\n");
//        userInput.append(prompt).append("\n");
//        userInput.append("分析需求:").append("\n").append(goal).append(",请使用").append(chartType).append("\n");
//
//
//        userInput.append("原始数据:").append("\n");
        // 压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);
//        userInput.append(result).append("\n");


        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus(TaskStatusEnum.WAIT.getStatus());

        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        long newCharId = chart.getId();
        biMessageProducer.sendMessage(String.valueOf(newCharId));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(newCharId);
        return ResultUtils.success(biResponse);


    }





}
