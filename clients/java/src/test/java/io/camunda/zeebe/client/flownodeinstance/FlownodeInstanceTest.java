/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.flownodeinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceFilterRequest;
import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FlownodeInstanceTest extends ClientRestTest {
  @Test
  void shouldSearchFlownodeInstance() {
    // when
    client.newFlownodeInstanceQuery().send().join();

    // then
    final FlownodeInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(FlownodeInstanceSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchFlownodeInstanceWithFullFilters() {
    // when
    client
        .newFlownodeInstanceQuery()
        .filter(
            f ->
                f.key(1L)
                    .type("type")
                    .state("state")
                    .processDefinitionKey(2L)
                    .processInstanceKey(3L)
                    .flowNodeId("flowNodeId")
                    .flowNodeName("flowNodeName")
                    .incident(true)
                    .incidentKey(4L)
                    .treePath("processInstanceKey/flowNodeId")
                    .tenantId("<default>"))
        .send()
        .join();
    // then
    final FlownodeInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(FlownodeInstanceSearchQueryRequest.class);
    final FlownodeInstanceFilterRequest filter = request.getFilter();
    assertThat(filter.getKey()).isEqualTo(1L);
    assertThat(filter.getType()).isEqualTo("type");
    assertThat(filter.getState()).isEqualTo("state");
    assertThat(filter.getProcessDefinitionKey()).isEqualTo(2L);
    assertThat(filter.getProcessInstanceKey()).isEqualTo(3L);
    assertThat(filter.getFlowNodeId()).isEqualTo("flowNodeId");
    assertThat(filter.getFlowNodeName()).isEqualTo("flowNodeName");
    assertThat(filter.getIncident()).isTrue();
    assertThat(filter.getIncidentKey()).isEqualTo(4L);
    assertThat(filter.getTreePath()).isEqualTo("processInstanceKey/flowNodeId");
    assertThat(filter.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldSearchFlownodeInstanceWithFullSorting() {
    // when
    client
        .newFlownodeInstanceQuery()
        .sort(
            s ->
                s.key()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .asc()
                    .flowNodeName()
                    .asc()
                    .type()
                    .asc()
                    .state()
                    .asc()
                    .startDate()
                    .desc()
                    .endDate()
                    .desc()
                    .incidentKey()
                    .asc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final FlownodeInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(FlownodeInstanceSearchQueryRequest.class);
    final List<SearchQuerySortRequest> sorts = request.getSort();
    assertThat(sorts.size()).isEqualTo(9);
    assertSort(sorts.get(0), "processDefinitionKey", "asc");
    assertSort(sorts.get(1), "processInstanceKey", "asc");
    assertSort(sorts.get(2), "flowNodeName", "asc");
    assertSort(sorts.get(3), "type", "asc");
    assertSort(sorts.get(4), "state", "asc");
    assertSort(sorts.get(5), "startDate", "desc");
    assertSort(sorts.get(6), "endDate", "desc");
    assertSort(sorts.get(7), "incidentKey", "asc");
    assertSort(sorts.get(8), "tenantId", "asc");
  }

  private void assertSort(
      final SearchQuerySortRequest sort, final String field, final String order) {
    assertThat(sort.getField()).isEqualTo(field);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
