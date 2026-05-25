package com.anomalydetection.detector.v2;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of {@link BaselineDataProvider} that bridges to the external module.
 * <p>
 * <b>TODO:</b> Replace placeholder return values with actual calls to the external module's API.
 * The external module provides both historical detection results and baseline statistics.
 * <p>
 * Current placeholder behavior: returns empty data with warning logs. This allows the
 * service to run (always in warmup mode, no history), which is useful for initial testing
 * and for resources with no prior detection history.
 */
public class ExternalBaselineProvider implements BaselineDataProvider {

    private static final Logger LOG = Logger.getLogger(ExternalBaselineProvider.class.getName());

    @Override
    public List<FeatureVector> getHistoryNormals(String resourceId) {
        // =====================================================================
        // TODO: 调用外部模块的接口获取该资源的所有正常历史特征向量
        //
        // 示例 (伪代码):
        //   OtherModule.ResultPage page = OtherModule.queryHistory(resourceId, "NORMAL");
        //   return page.getVectors().stream()
        //       .map(dto -> convertToFeatureVector(dto))
        //       .collect(Collectors.toList());
        //
        // 参数: resourceId - 资源标识符
        // 返回: 正常历史特征向量列表（空列表表示无历史数据）
        // =====================================================================
        LOG.warning("[NOT YET IMPLEMENTED] getHistoryNormals(" + resourceId
                + ") — returning empty list. Implement this to query external module.");
        return Collections.emptyList();
    }

    @Override
    public List<FeatureVector> getHistoryAnomalies(String resourceId) {
        // =====================================================================
        // TODO: 调用外部模块的接口获取该资源的所有异常历史特征向量
        //
        // 与 getHistoryNormals 类似，但查询条件是 "ANOMALY" 或 "SUSPICIOUS"
        // =====================================================================
        LOG.warning("[NOT YET IMPLEMENTED] getHistoryAnomalies(" + resourceId
                + ") — returning empty list.");
        return Collections.emptyList();
    }

    @Override
    public BaselineStatsDTO getBaselineStats(String resourceId) {
        // =====================================================================
        // TODO: 调用外部模块的接口获取该资源的基线统计量
        //
        // 示例 (伪代码):
        //   OtherModule.BaselineDTO dto = OtherModule.queryBaseline(resourceId);
        //   return new BaselineStatsDTO(resourceId, dto.getMedian(), dto.getMad(), dto.getThreshold());
        //
        // BaselineStatsDTO 包含: median[14], mad[14], threshold
        // 注意: weights 不包含在此 DTO 中，需通过 WeightService 从数据库查询
        // =====================================================================
        LOG.warning("[NOT YET IMPLEMENTED] getBaselineStats(" + resourceId
                + ") — returning null. Active phase will fall back to warmup.");
        return null;
    }
}
