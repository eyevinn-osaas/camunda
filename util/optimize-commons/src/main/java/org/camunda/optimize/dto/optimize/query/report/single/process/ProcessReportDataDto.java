/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@SuperBuilder
@FieldNameConstants
public class ProcessReportDataDto extends SingleReportDataDto implements Combinable {

  private static final String COMMAND_KEY_SEPARATOR = "_";
  private static final String MISSING_COMMAND_PART_PLACEHOLDER = "null";

  protected String processDefinitionKey;
  @Builder.Default
  protected List<String> processDefinitionVersions = new ArrayList<>();
  protected String processDefinitionName;
  @Builder.Default
  protected List<String> tenantIds = Collections.singletonList(null);
  @Builder.Default
  protected List<ProcessFilterDto<?>> filter = new ArrayList<>();
  protected ProcessViewDto view;
  protected ProcessGroupByDto<?> groupBy;
  @Builder.Default
  protected ProcessDistributedByDto<?> distributedBy = new ProcessDistributedByDto<>();
  protected ProcessVisualization visualization;

  @JsonIgnore
  @Override
  public String getDefinitionKey() {
    return processDefinitionKey;
  }

  @JsonIgnore
  @Override
  public List<String> getDefinitionVersions() {
    return processDefinitionVersions;
  }

  @JsonIgnore
  @Override
  public String getDefinitionName() {
    return processDefinitionName;
  }

  @JsonIgnore
  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  @Override
  public List<ViewProperty> getViewProperties() {
    return view.getProperties();
  }

  @JsonIgnore
  public boolean isFrequencyReport() {
    return Optional.ofNullable(view)
      .map(ProcessViewDto::getFirstProperty)
      .map(p -> p.equals(ViewProperty.FREQUENCY))
      .orElse(false);
  }

  @Override
  public List<String> createCommandKeys() {
    final String groupByCommandKey = groupBy == null ? MISSING_COMMAND_PART_PLACEHOLDER : groupBy.createCommandKey();
    String distributedByCommandKey = createDistributedByCommandKey();
    String configurationCommandKey = Optional.ofNullable(getConfiguration())
      .map(SingleReportConfigurationDto::createCommandKey)
      .orElse(MISSING_COMMAND_PART_PLACEHOLDER);
    return Optional.ofNullable(view)
      .map(ProcessViewDto::createCommandKeys)
      .orElse(Collections.singletonList(MISSING_COMMAND_PART_PLACEHOLDER))
      .stream()
      .map(viewKey -> String.join(
        COMMAND_KEY_SEPARATOR, viewKey, groupByCommandKey, distributedByCommandKey, configurationCommandKey
      ))
      .collect(Collectors.toList());
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    return createCommandKeys().get(0);
  }

  public String createDistributedByCommandKey() {
    if (distributedBy != null
      && (isModelElementCommand() || isInstanceCommand())) {
      return distributedBy.createCommandKey();
    }
    return null;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessReportDataDto)) {
      return false;
    }
    ProcessReportDataDto that = (ProcessReportDataDto) o;
    return Combinable.isCombinable(getView(), that.getView())
      && isGroupByCombinable(that)
      && Combinable.isCombinable(getDistributedBy(), that.getDistributedBy())
      && Objects.equals(getVisualization(), that.getVisualization())
      && getConfiguration().isCombinable(that.getConfiguration());
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  @JsonIgnore
  public List<ProcessFilterDto<?>> getAdditionalFiltersForReportType() {
    if (isGroupByEndDateReport()) {
      return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
    }
    // TODO OPT-5203 add "real" flowNodeType filter for userTask reports with follow up task
    return Collections.emptyList();
  }

  public boolean isUserTaskReport() {
    return nonNull(view) && ProcessViewEntity.USER_TASK.equals(view.getEntity());
  }

  private boolean isGroupByCombinable(final ProcessReportDataDto that) {
    if (Combinable.isCombinable(this.groupBy, that.groupBy)) {
      if (isGroupByDateVariableReport()) {
        return this.getConfiguration()
          .getGroupByDateVariableUnit()
          .equals(that.getConfiguration().getGroupByDateVariableUnit());
      } else if (isGroupByNumberReport()) {
        return isBucketSizeCombinable(that);
      }
      return true;
    }
    return false;
  }

  private boolean isBucketSizeCombinable(final ProcessReportDataDto that) {
    return this.getConfiguration().getCustomBucket().isActive()
      && that.getConfiguration().getCustomBucket().isActive()
      && Objects.equals(
      this.getConfiguration().getCustomBucket().getBucketSize(),
      that.getConfiguration().getCustomBucket().getBucketSize()
    ) || isBucketSizeIrrelevant(this) && isBucketSizeIrrelevant(that);
  }

  private boolean isBucketSizeIrrelevant(final ProcessReportDataDto reportData) {
    // Bucket size settings for combined reports are not relevant if custom bucket config is
    // inactive or bucket size is null
    if (reportData.getConfiguration().getCustomBucket().isActive()) {
      return reportData.getConfiguration().getCustomBucket().getBucketSize() == null;
    }
    return true;
  }

  private boolean isGroupByDateVariableReport() {
    return groupBy != null
      && ProcessGroupByType.VARIABLE.equals(groupBy.getType())
      && VariableType.DATE.equals(((VariableGroupByDto) groupBy).getValue().getType());
  }

  private boolean isGroupByEndDateReport() {
    return groupBy != null
      && ProcessViewEntity.PROCESS_INSTANCE.equals(view.getEntity())
      && ProcessGroupByType.END_DATE.equals(groupBy.getType());
  }

  private boolean isGroupByNumberReport() {
    return groupBy != null
      &&
      (
        ProcessGroupByType.VARIABLE.equals(groupBy.getType())
          && (VariableType.getNumericTypes().contains(((VariableGroupByDto) groupBy).getValue().getType()))
          || ProcessGroupByType.DURATION.equals(groupBy.getType())
      );
  }

  private boolean isModelElementCommand() {
    return nonNull(view) && nonNull(view.getEntity()) &&
      (ProcessViewEntity.USER_TASK.equals(view.getEntity())
        || ProcessViewEntity.FLOW_NODE.equals(view.getEntity()));
  }

  private boolean isInstanceCommand() {
    return nonNull(view)
      && nonNull(view.getEntity())
      && ProcessViewEntity.PROCESS_INSTANCE.equals(view.getEntity());
  }

}
