package io.hashmatrix.governance.domain.metadata;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资产检索的纯领域逻辑：在**已圈定的资产集合**（来源无关——mock 种子或本租户落库资产）上，
 * 施加关键字匹配、类型过滤、分面统计与分页，产出契约 {@link MetaSearchResult}。
 *
 * <p>抽出为共享组件，使 {@code infra} 的 mock 适配器与 PG 适配器（#28）**复用同一检索语义**——
 * 适配器只负责「圈定来源集合 + 租户隔离」，检索/分面/分页一处实现、行为一致（DRY，杜绝两套语义漂移）。
 *
 * <p>分面在「关键字命中集」上统计（含各类型计数，供前端继续按类型收窄）；类型过滤再作用于结果列表
 * （与原 mock 语义一致）。无副作用、纯函数。
 */
public final class AssetCatalogSearch {

    private AssetCatalogSearch() {}

    /**
     * 在给定资产集合上执行检索。
     *
     * @param source 候选资产（调用方已完成租户圈定/隔离）
     * @param query  检索条件
     * @return 分页 + 分面结果
     */
    public static MetaSearchResult search(List<AssetSummary> source, SearchQuery query) {
        List<AssetSummary> keywordMatched =
                source.stream().filter(asset -> matchesKeyword(asset, query.q())).toList();
        List<Facet> facets = facetByType(keywordMatched);

        List<AssetSummary> matched =
                (query.type() == null)
                        ? keywordMatched
                        : keywordMatched.stream()
                                .filter(asset -> asset.type() == query.type())
                                .toList();

        int total = matched.size();
        int from = Math.min((query.page() - 1) * query.pageSize(), total);
        int to = Math.min(from + query.pageSize(), total);
        List<AssetSummary> pageItems = matched.subList(from, to);

        return new MetaSearchResult(pageItems, query.page(), query.pageSize(), total, facets);
    }

    private static boolean matchesKeyword(AssetSummary asset, String q) {
        if (q == null) {
            return true;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        if (asset.name().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return asset.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static List<Facet> facetByType(List<AssetSummary> assets) {
        Map<AssetType, Long> counts =
                assets.stream()
                        .collect(Collectors.groupingBy(AssetSummary::type, Collectors.counting()));
        List<FacetBucket> buckets =
                counts.entrySet().stream()
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                        .map(entry -> new FacetBucket(entry.getKey().wire(), Math.toIntExact(entry.getValue())))
                        .toList();
        return buckets.isEmpty() ? List.of() : List.of(new Facet("type", buckets));
    }
}
