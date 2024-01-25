/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class FlowNodeInstanceDaoIT extends OperateSearchAbstractIT {

  @Autowired
  private FlowNodeInstanceDao dao;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @MockBean
  private ProcessCache processCache;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685256L).setProcessInstanceKey(2251799813685253L).setProcessDefinitionKey(2251799813685249L)
        .setStartDate("2024-01-19T18:39:05.196+0000").setEndDate("2024-01-19T18:39:05.196+0000").setFlowNodeId("start").setType("START_EVENT")
        .setState("COMPLETED").setIncident(false).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new FlowNodeInstance().setKey(2251799813685258L).setProcessInstanceKey(2251799813685253L).setProcessDefinitionKey(2251799813685249L)
        .setStartDate("2024-01-19T18:39:05.196+0000").setEndDate("2024-01-19T18:39:05.196+0000").setFlowNodeId("taskA").setType("SERVICE_TASK")
        .setIncidentKey(2251799813685264L).setState("ACTIVE").setIncident(true).setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Override
  public void runAdditionalBeforeEachSetup() {
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("start"), eq(null))).thenReturn("start");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskA"), eq(null))).thenReturn("task A");
  }

  @Test
  public void shouldReturnFlowNodeInstances() {
    Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<>());

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);

    FlowNodeInstance checkFlowNode = flowNodeInstanceResults.getItems().stream().filter(
            item -> "START_EVENT".equals(item.getType()))
        .findFirst().orElse(null);
    assertThat(checkFlowNode).extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    checkFlowNode = flowNodeInstanceResults.getItems().stream().filter(
            item -> "SERVICE_TASK".equals(item.getType()))
        .findFirst().orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldFilterFlowNodeInstances() {
    Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
        .setFilter(new FlowNodeInstance().setType("SERVICE_TASK")));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0)).extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldSortFlowNodeInstancesAsc() {
    Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
        .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC)));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    assertThat(flowNodeInstanceResults.getItems().get(1))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldSortFlowNodeInstancesDesc() {
    Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
        .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.DESC)));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);
    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");

    assertThat(flowNodeInstanceResults.getItems().get(1))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");
  }

  @Test
  public void shouldPageFlowNodeInstances() {
    Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>().setSize(1)
        .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2);
    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    Object[] searchAfter = flowNodeInstanceResults.getSortValues();

    flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>().setSize(3)
        .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC)).setSearchAfter(searchAfter));
    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2);
    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldReturnByKey() {
    FlowNodeInstance flowNodeInstance = dao.byKey(2251799813685258L);

    assertThat(flowNodeInstance).extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }
}
