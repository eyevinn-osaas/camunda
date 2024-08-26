/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.date;

import static io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.ProcessGroupByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import io.camunda.optimize.service.db.es.report.command.util.DateAggregationContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;

public abstract class AbstractProcessGroupByModelElementDate extends ProcessGroupByPart {

  private static final String ELEMENT_AGGREGATION = "elementAggregation";
  private static final String FILTERED_ELEMENTS_AGGREGATION = "filteredElements";
  private static final String MODEL_ELEMENT_TYPE_FILTER_AGGREGATION = "filteredElementsByType";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractProcessGroupByModelElementDate.class);

  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  public AbstractProcessGroupByModelElementDate(
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService) {
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
  }

  protected abstract String getPathToElementField();

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    final MinMaxStatDto stats =
        minMaxStatsService.getMinMaxDateRangeForNestedField(
            context,
            searchSourceBuilder.query(),
            getIndexNames(context),
            getDateField(),
            getPathToElementField(),
            getFilterQuery(context));

    final DateAggregationContext dateAggContext =
        DateAggregationContext.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(distributedByPart.createAggregations(context))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<AggregationBuilder> bucketLimitedHistogramAggregation =
        dateAggregationService.createModelElementDateAggregation(dateAggContext);

    if (bucketLimitedHistogramAggregation.isPresent()) {
      final NestedAggregationBuilder groupByFlowNodeDateAggregation =
          wrapInNestedElementAggregation(
              context,
              bucketLimitedHistogramAggregation.get(),
              distributedByPart.createAggregations(context));
      return Collections.singletonList(groupByFlowNodeDateAggregation);
    }

    return Collections.emptyList();
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto> context, final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final AggregateByDateUnit groupByDateUnit = getGroupByDateUnit(context.getReportData());
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDateUnit)) {
        return Optional.of(
            minMaxStatsService.getMinMaxDateRangeForNestedField(
                context,
                baseQuery,
                getIndexNames(context),
                getDateField(),
                getPathToElementField(),
                getFilterQuery(context)));
      }
    }
    return Optional.empty();
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
  }

  private NestedAggregationBuilder wrapInNestedElementAggregation(
      final ExecutionContext<ProcessReportDataDto> context,
      final AggregationBuilder aggregationToWrap,
      final List<AggregationBuilder> distributedBySubAggregations) {
    final FilterAggregationBuilder filteredElementsAggregation =
        filter(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION, getModelElementTypeFilterQuery())
            .subAggregation(
                filter(FILTERED_ELEMENTS_AGGREGATION, getFilterQuery(context))
                    .subAggregation(aggregationToWrap));

    // sibling aggregation next to filtered userTask agg for distributedByPart for retrieval of all
    // keys that
    // should be present in distributedBy result via enrichContextWithAllExpectedDistributedByKeys
    distributedBySubAggregations.forEach(filteredElementsAggregation::subAggregation);

    return nested(ELEMENT_AGGREGATION, getPathToElementField())
        .subAggregation(filteredElementsAggregation);
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse response, final ExecutionContext<ProcessReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();

    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Nested flowNodes = aggregations.get(ELEMENT_AGGREGATION);
    final Filter filteredFlowNodesByType =
        flowNodes.getAggregations().get(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION);
    final Filter filteredFlowNodes =
        filteredFlowNodesByType.getAggregations().get(FILTERED_ELEMENTS_AGGREGATION);
    final Optional<Aggregations> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(filteredFlowNodes.getAggregations());

    distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
        context, filteredFlowNodesByType.getAggregations());

    final Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap =
          dateAggregationService.mapDateAggregationsToKeyAggregationMap(
              unwrappedLimitedAggregations.get(), context.getTimezone());
    } else {
      return Collections.emptyList();
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Aggregations> keyToAggregationMap,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    distributedByPart.retrieveResult(
                        response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  protected abstract String getDateField();

  protected abstract QueryBuilder getFilterQuery(
      final ExecutionContext<ProcessReportDataDto> context);

  protected abstract QueryBuilder getModelElementTypeFilterQuery();
}
