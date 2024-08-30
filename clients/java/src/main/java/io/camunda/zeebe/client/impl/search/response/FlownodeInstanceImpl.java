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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.FlownodeInstance;
import io.camunda.zeebe.client.protocol.rest.FlownodeInstanceItem;

public final class FlownodeInstanceImpl implements FlownodeInstance {

  private final Long key;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final String flowNodeId;
  private final String flowNodeName;
  private final String startDate;
  private final String endDate;
  private final Boolean incident;
  private final Long incidentKey;
  private final String state;
  private final String tenantId;
  private final String treePath;
  private final String type;

  public FlownodeInstanceImpl(final FlownodeInstanceItem item) {
    key = item.getKey();
    processDefinitionKey = item.getProcessDefinitionKey();
    processInstanceKey = item.getProcessInstanceKey();
    flowNodeId = item.getFlowNodeId();
    flowNodeName = item.getFlowNodeName();
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    incident = item.getIncident();
    incidentKey = item.getIncidentKey();
    state = item.getState();
    tenantId = item.getTenantId();
    treePath = item.getTreePath();
    type = item.getType();
  }

  @Override
  public Long getKey() {
    return key;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public String getFlowNodeName() {
    return flowNodeName;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }

  @Override
  public Boolean getIncident() {
    return incident;
  }

  @Override
  public Long getIncidentKey() {
    return incidentKey;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getTreePath() {
    return treePath;
  }

  @Override
  public String getType() {
    return type;
  }
}
