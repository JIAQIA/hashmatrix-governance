package io.hashmatrix.governance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hashmatrix.governance.app.MetadataCommandService;
import io.hashmatrix.governance.app.MetadataQueryService;
import io.hashmatrix.governance.domain.metadata.AssetType;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock private MetadataQueryService service;
    @Mock private MetadataCommandService commandService;

    @Test
    void appliesContractDefaultsAndParsesType() {
        MetaSearchResult stub = new MetaSearchResult(List.of(), 1, 20, 0, List.of());
        when(service.search(any())).thenReturn(stub);
        MetadataController controller = new MetadataController(service, commandService);

        controller.search("orders", "table", null, null);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(service).search(captor.capture());
        SearchQuery query = captor.getValue();
        assertThat(query.q()).isEqualTo("orders");
        assertThat(query.type()).isEqualTo(AssetType.TABLE);
        assertThat(query.page()).isEqualTo(SearchQuery.DEFAULT_PAGE);
        assertThat(query.pageSize()).isEqualTo(SearchQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void toleratesUnknownAndBlankTypeAsNoFilter() {
        // 契约仅声明 200：不可识别（"bogus"）与空串（前端清空筛选常发 type=）均退化为「不过滤」，不抛 4xx。
        MetaSearchResult stub = new MetaSearchResult(List.of(), 1, 20, 0, List.of());
        when(service.search(any())).thenReturn(stub);
        MetadataController controller = new MetadataController(service, commandService);

        controller.search(null, "bogus", null, null);
        controller.search(null, "", null, null);

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(service, times(2)).search(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(query -> assertThat(query.type()).isNull());
    }
}
