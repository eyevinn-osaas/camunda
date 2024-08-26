/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.LOWERCASE_FIELD;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.N_GRAM_FIELD;
import static io.camunda.optimize.service.util.DecisionVariableHelper.buildWildcardQuery;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getValueSearchField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableStringValueField;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtil;
import io.camunda.optimize.service.util.DecisionVariableHelper;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DecisionVariableQueryFilter extends AbstractVariableQueryFilter
    implements QueryFilter<VariableFilterDataDto<?>> {

  private static final Logger log = LoggerFactory.getLogger(DecisionVariableQueryFilter.class);
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  public DecisionVariableQueryFilter() {}

  abstract String getVariablePath();

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<VariableFilterDataDto<?>> variableFilters,
      final FilterContext filterContext) {
    if (variableFilters != null) {
      final List<QueryBuilder> filters = query.filter();
      for (final VariableFilterDataDto<?> variable : variableFilters) {
        filters.add(createFilterQueryBuilder(variable, filterContext.getTimezone()));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(
      final VariableFilterDataDto<?> dto, final ZoneId timezone) {
    ValidationHelper.ensureNotNull("Variable filter data", dto.getData());

    QueryBuilder queryBuilder = matchAllQuery();

    switch (dto.getType()) {
      case BOOLEAN:
        final BooleanVariableFilterDataDto booleanVarDto = (BooleanVariableFilterDataDto) dto;
        queryBuilder = createBooleanQueryBuilder(booleanVarDto);
        break;
      case STRING:
        final StringVariableFilterDataDto stringVarDto = (StringVariableFilterDataDto) dto;
        queryBuilder = createStringQueryBuilder(stringVarDto);
        break;
      case INTEGER:
      case DOUBLE:
      case SHORT:
      case LONG:
        final OperatorMultipleValuesVariableFilterDataDto numericVarDto =
            (OperatorMultipleValuesVariableFilterDataDto) dto;
        queryBuilder = createNumericQueryBuilder(numericVarDto);
        break;
      case DATE:
        final DateVariableFilterDataDto dateVarDto = (DateVariableFilterDataDto) dto;
        queryBuilder = createDateQueryBuilder(dateVarDto, timezone);
        break;
      default:
        logger.warn(
            "Could not filter for variables! Type [{}] is not supported for variable filters. Ignoring filter.",
            dto.getType());
    }
    return queryBuilder;
  }

  @Override
  protected QueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final StringVariableFilterDataDto dto) {
    final BoolQueryBuilder containOneOfTheGivenStrings =
        createContainsOneOfTheGivenStringsQueryBuilder(dto.getName(), dto.getData().getValues());

    if (NOT_CONTAINS.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(containOneOfTheGivenStrings);
    } else {
      return containOneOfTheGivenStrings;
    }
  }

  @Override
  protected BoolQueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final String variableId, final List<String> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);

    values.stream()
        .filter(Objects::nonNull)
        .forEach(
            stringVal ->
                variableFilterBuilder.should(
                    createContainsGivenStringQuery(variableId, stringVal)));

    final boolean hasNullValues = values.stream().anyMatch(Objects::isNull);
    if (hasNullValues) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(variableId));
    }

    return variableFilterBuilder;
  }

  @Override
  protected QueryBuilder createContainsGivenStringQuery(
      final String variableId, final String valueToContain) {

    final BoolQueryBuilder containsVariableString =
        boolQuery().must(termQuery(getVariableIdField(), variableId));

    final String lowerCaseValue = valueToContain.toLowerCase(Locale.ENGLISH);
    final QueryBuilder filter =
        (lowerCaseValue.length() > MAX_GRAM)
            /*
              using the slow wildcard query for uncommonly large filter strings (> 10 chars)
            */
            ? wildcardQuery(
                getValueSearchField(getVariablePath(), LOWERCASE_FIELD),
                buildWildcardQuery(lowerCaseValue))
            /*
              using Elasticsearch ngrams to filter for strings < 10 chars,
              because it's fast but increasing the number of chars makes the index bigger
            */
            : termQuery(getValueSearchField(getVariablePath(), N_GRAM_FIELD), lowerCaseValue);

    containsVariableString.must(filter);

    return nestedQuery(getVariablePath(), containsVariableString, ScoreMode.None);
  }

  @Override
  protected QueryBuilder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    final BoolQueryBuilder variableFilterBuilder =
        createMultiValueVariableFilterQuery(
            getVariableId(dto), dto.getType(), dto.getData().getValues());

    if (NOT_IN.equals(dto.getData().getOperator())) {
      return boolQuery().mustNot(variableFilterBuilder);
    } else {
      return variableFilterBuilder;
    }
  }

  @Override
  protected QueryBuilder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto) {
    ValidationHelper.ensureCollectionNotEmpty("boolean filter value", dto.getData().getValues());

    return createMultiValueVariableFilterQuery(
        getVariableId(dto), dto.getType(), dto.getData().getValues());
  }

  @Override
  protected QueryBuilder createNumericQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    validateMultipleValuesFilterDataDto(dto);

    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(dto.getType());
    final OperatorMultipleValuesFilterDataDto data = dto.getData();
    final BoolQueryBuilder boolQueryBuilder =
        boolQuery().must(termQuery(getVariableIdField(), getVariableId(dto)));

    if (data.getValues().isEmpty()) {
      logger.warn(
          "Could not filter for variables! No values provided for operator [{}] and type [{}]. Ignoring filter.",
          data.getOperator(),
          dto.getType());
      return boolQueryBuilder;
    }

    QueryBuilder resultQuery = nestedQuery(getVariablePath(), boolQueryBuilder, ScoreMode.None);
    final Object value = retrieveValue(dto);
    switch (data.getOperator()) {
      case IN:
      case NOT_IN:
        resultQuery = createEqualsOneOrMoreValuesQueryBuilder(dto);
        break;
      case LESS_THAN:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).lt(value));
        break;
      case GREATER_THAN:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).gt(value));
        break;
      case LESS_THAN_EQUALS:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).lte(value));
        break;
      case GREATER_THAN_EQUALS:
        boolQueryBuilder.must(rangeQuery(nestedVariableValueFieldLabel).gte(value));
        break;
      default:
        logger.warn(
            "Could not filter for variables! Operator [{}] is not supported for type [{}]. Ignoring filter.",
            data.getOperator(),
            dto.getType());
    }
    return resultQuery;
  }

  @Override
  protected QueryBuilder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone) {
    final BoolQueryBuilder dateFilterBuilder = boolQuery().minimumShouldMatch(1);

    if (dto.getData().isIncludeUndefined()) {
      dateFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(getVariableId(dto)));
    } else if (dto.getData().isExcludeUndefined()) {
      dateFilterBuilder.should(createExcludeUndefinedOrNullQueryBuilder(getVariableId(dto)));
    }

    final BoolQueryBuilder dateValueFilterQuery =
        boolQuery().must(termQuery(getVariableIdField(), getVariableId(dto)));
    DateFilterQueryUtil.addFilters(
        dateValueFilterQuery,
        Collections.singletonList(dto.getData()),
        getVariableValueFieldForType(dto.getType()),
        timezone);
    if (!dateValueFilterQuery.filter().isEmpty()) {
      dateFilterBuilder.should(
          nestedQuery(getVariablePath(), dateValueFilterQuery, ScoreMode.None));
    }

    return dateFilterBuilder;
  }

  private BoolQueryBuilder createMultiValueVariableFilterQuery(
      final String variableId, final VariableType variableType, final List<?> values) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery().minimumShouldMatch(1);
    final String nestedVariableIdFieldLabel = getVariableIdField();
    final String nestedVariableValueFieldLabel = getVariableValueFieldForType(variableType);

    final List<?> nonNullValues =
        values.stream().filter(Objects::nonNull).collect(Collectors.toList());

    if (!nonNullValues.isEmpty()) {
      variableFilterBuilder.should(
          nestedQuery(
              getVariablePath(),
              boolQuery()
                  .must(termQuery(nestedVariableIdFieldLabel, variableId))
                  .must(termsQuery(nestedVariableValueFieldLabel, nonNullValues)),
              ScoreMode.None));
    }

    if (nonNullValues.size() < values.size()) {
      variableFilterBuilder.should(createFilterForUndefinedOrNullQueryBuilder(variableId));
    }
    return variableFilterBuilder;
  }

  private QueryBuilder createFilterForUndefinedOrNullQueryBuilder(final String variableId) {
    return boolQuery()
        .should(
            // undefined
            boolQuery()
                .mustNot(
                    nestedQuery(
                        getVariablePath(),
                        termQuery(getVariableIdField(), variableId),
                        ScoreMode.None)))
        .should(
            // or null value
            boolQuery()
                .must(
                    nestedQuery(
                        getVariablePath(),
                        boolQuery()
                            .must(termQuery(getVariableIdField(), variableId))
                            .mustNot(existsQuery(getVariableStringValueField(getVariablePath()))),
                        ScoreMode.None)))
        .minimumShouldMatch(1);
  }

  private QueryBuilder createExcludeUndefinedOrNullQueryBuilder(final String variableId) {
    return boolQuery()
        .must(
            nestedQuery(
                getVariablePath(),
                boolQuery()
                    .must(termQuery(getVariableIdField(), variableId))
                    .must(existsQuery(getVariableStringValueField(getVariablePath()))),
                ScoreMode.None));
  }

  private String getVariableId(final VariableFilterDataDto<?> dto) {
    // the input/output variable id is stored as name as we use the same dto's as for process
    // filters here
    // with https://jira.camunda.com/browse/OPT-1942 we intend to introduce a dedicated dto to make
    // the difference clear
    return dto.getName();
  }

  private String getVariableValueFieldForType(final VariableType type) {
    return DecisionVariableHelper.getVariableValueFieldForType(getVariablePath(), type);
  }

  private String getVariableIdField() {
    return DecisionVariableHelper.getVariableClauseIdField(getVariablePath());
  }
}
