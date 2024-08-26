/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.usertask;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByPart;
import java.util.Optional;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;

public abstract class AbstractGroupByUserTask extends ProcessGroupByPart {

  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FLOW_NODE_AGGREGATION = "flowNodes";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";

  protected final DefinitionService definitionService;

  public AbstractGroupByUserTask(final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  protected NestedAggregationBuilder createFilteredUserTaskAggregation(
      final ExecutionContext<ProcessReportDataDto> context,
      final AggregationBuilder subAggregation) {
    final FilterAggregationBuilder filteredUserTaskAggregation =
        filter(USER_TASKS_AGGREGATION, createUserTaskFlowNodeTypeFilter())
            .subAggregation(
                filter(
                        FILTERED_USER_TASKS_AGGREGATION,
                        createModelElementAggregationFilter(
                            context.getReportData(), context.getFilterContext(), definitionService))
                    .subAggregation(subAggregation));

    // sibling aggregation next to filtered userTask agg for distributedByPart for retrieval of all
    // keys that
    // should be present in distributedBy result via enrichContextWithAllExpectedDistributedByKeys
    distributedByPart
        .createAggregations(context)
        .forEach(filteredUserTaskAggregation::subAggregation);

    return nested(FLOW_NODE_AGGREGATION, FLOW_NODE_INSTANCES)
        .subAggregation(filteredUserTaskAggregation);
  }

  protected Optional<Filter> getFilteredUserTaskAggregation(final SearchResponse response) {
    return getUserTasksAggregation(response)
        .map(SingleBucketAggregation::getAggregations)
        .map(aggs -> aggs.get(FILTERED_USER_TASKS_AGGREGATION));
  }

  protected Optional<ParsedFilter> getUserTasksAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
        .map(aggs -> aggs.get(FLOW_NODE_AGGREGATION))
        .map(flowNodeAgg -> ((Nested) flowNodeAgg).getAggregations().get(USER_TASKS_AGGREGATION));
  }
}
