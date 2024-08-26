/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.report.command.modules.group_by.AbstractGroupByVariable.FILTERED_FLOW_NODE_AGGREGATION;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.util.DateAggregationContext;
import io.camunda.optimize.service.db.es.report.command.util.VariableAggregationContext;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.springframework.stereotype.Component;

@Component
public class VariableAggregationService {

  public static final String NESTED_VARIABLE_AGGREGATION = "nestedVariables";
  public static final String NESTED_FLOWNODE_AGGREGATION = "nestedFlowNodes";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  public static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  public static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "instCount";
  public static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  public static final String VARIABLE_HISTOGRAM_AGGREGATION = "numberVariableHistogram";

  private final ConfigurationService configurationService;
  private final NumberVariableAggregationService numberVariableAggregationService;
  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  public VariableAggregationService(
      final ConfigurationService configurationService,
      final NumberVariableAggregationService numberVariableAggregationService,
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService) {
    this.configurationService = configurationService;
    this.numberVariableAggregationService = numberVariableAggregationService;
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
  }

  public Optional<AggregationBuilder> createVariableSubAggregation(
      final VariableAggregationContext context) {
    context.setVariableRangeMinMaxStats(getVariableMinMaxStats(context));
    switch (context.getVariableType()) {
      case STRING:
      case BOOLEAN:
        final TermsAggregationBuilder variableTermsAggregation =
            AggregationBuilders.terms(VARIABLES_AGGREGATION)
                .size(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getAggregationBucketLimit())
                .field(context.getNestedVariableValueFieldLabel());
        context.getSubAggregations().forEach(variableTermsAggregation::subAggregation);
        return Optional.of(variableTermsAggregation);
      case DATE:
        return createDateVariableAggregation(context);
      default:
        if (VariableType.getNumericTypes().contains(context.getVariableType())) {
          return numberVariableAggregationService.createNumberVariableAggregation(context);
        }

        return Optional.empty();
    }
  }

  public Optional<QueryBuilder> createVariableFilterQuery(
      final VariableAggregationContext context) {
    if (VariableType.getNumericTypes().contains(context.getVariableType())) {
      return numberVariableAggregationService
          .getBaselineForNumberVariableAggregation(context)
          .filter(baseLineValue -> !baseLineValue.isNaN())
          .map(
              baseLineValue ->
                  QueryBuilders.rangeQuery(context.getNestedVariableValueFieldLabel())
                      .lte(context.getMaxVariableValue())
                      .gte(baseLineValue));
    } else {
      return Optional.empty();
    }
  }

  private Optional<AggregationBuilder> createDateVariableAggregation(
      final VariableAggregationContext context) {
    final DateAggregationContext dateAggContext =
        DateAggregationContext.builder()
            .aggregateByDateUnit(context.getDateUnit())
            .dateField(context.getNestedVariableValueFieldLabel())
            .minMaxStats(context.getVariableRangeMinMaxStats())
            .timezone(context.getTimezone())
            .subAggregations(context.getSubAggregations())
            .dateAggregationName(VARIABLES_AGGREGATION)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService.createDateVariableAggregation(dateAggContext);
  }

  public Map<String, Aggregations> retrieveResultBucketMap(
      final Filter filteredParentAgg,
      final MultiBucketsAggregation variableTermsAgg,
      final VariableType variableType,
      final ZoneId timezone) {
    final Map<String, Aggregations> bucketAggregations;
    if (VariableType.DATE.equals(variableType)) {
      bucketAggregations =
          dateAggregationService.mapDateAggregationsToKeyAggregationMap(
              filteredParentAgg.getAggregations(), timezone, VARIABLES_AGGREGATION);
    } else {
      bucketAggregations =
          variableTermsAgg.getBuckets().stream()
              .collect(
                  toMap(
                      MultiBucketsAggregation.Bucket::getKeyAsString,
                      MultiBucketsAggregation.Bucket::getAggregations,
                      (bucketAggs1, bucketAggs2) -> bucketAggs1));
    }
    return bucketAggregations;
  }

  public Aggregations retrieveSubAggregationFromBucketMapEntry(
      final Map.Entry<String, Aggregations> bucketMapEntry) {
    final ReverseNested reverseNested =
        bucketMapEntry.getValue().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
    if (reverseNested == null) {
      return bucketMapEntry.getValue();
    } else {
      final ParsedNested nestedFlowNodeAgg =
          reverseNested.getAggregations().get(NESTED_FLOWNODE_AGGREGATION);
      if (nestedFlowNodeAgg == null) {
        return reverseNested.getAggregations(); // this is an instance report
      } else {
        final Aggregations flowNodeAggs =
            nestedFlowNodeAgg.getAggregations(); // this is a flownode report
        final ParsedFilter aggregation = flowNodeAggs.get(FILTERED_FLOW_NODE_AGGREGATION);
        return aggregation.getAggregations();
      }
    }
  }

  private MinMaxStatDto getVariableMinMaxStats(final VariableAggregationContext context) {
    return getVariableMinMaxStats(
        context.getVariableType(),
        context.getVariableName(),
        context.getVariablePath(),
        context.getNestedVariableNameField(),
        context.getNestedVariableValueFieldLabel(),
        context.getIndexNames(),
        context.getBaseQueryForMinMaxStats());
  }

  public MinMaxStatDto getVariableMinMaxStats(
      final VariableType variableType,
      final String variableName,
      final String variablePath,
      final String nestedVariableNameField,
      final String nestedVariableValueFieldLabel,
      final String[] indexNames,
      final QueryBuilder baseQuery) {
    final BoolQueryBuilder filterQuery =
        boolQuery().must(termQuery(nestedVariableNameField, variableName));
    if (VariableType.getNumericTypes().contains(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
          baseQuery, indexNames, nestedVariableValueFieldLabel, variablePath, filterQuery);
    } else if (VariableType.DATE.equals(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
          baseQuery,
          indexNames,
          nestedVariableValueFieldLabel,
          OPTIMIZE_DATE_FORMAT,
          variablePath,
          filterQuery);
    }
    return new MinMaxStatDto(Double.NaN, Double.NaN); // not used for other variable types
  }
}
