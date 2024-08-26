/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.slf4j.Logger;

public class ElasticsearchCompositeAggregationScroller {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchCompositeAggregationScroller.class);
  private OptimizeElasticsearchClient esClient;
  private SearchRequest searchRequest;
  private Consumer<ParsedComposite.ParsedBucket> compositeBucketConsumer;
  private LinkedList<String> pathToAggregation;

  public static ElasticsearchCompositeAggregationScroller create() {
    return new ElasticsearchCompositeAggregationScroller();
  }

  public void consumeAllPages() {
    boolean pageConsumed;
    do {
      pageConsumed = consumePage();
    } while (pageConsumed);
  }

  /**
   * Consumes next page of the composite aggregation.
   *
   * @return {@code true} if a page was present, {@code false} else
   */
  public boolean consumePage() {
    final List<ParsedComposite.ParsedBucket> currentPage = getNextPage();
    currentPage.forEach(compositeBucketConsumer);
    return !currentPage.isEmpty();
  }

  private List<ParsedComposite.ParsedBucket> getNextPage() {
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      final ParsedComposite compositeAggregationResult =
          extractCompositeAggregationResult(searchResponse);

      // find aggregation and adjust after key for next invocation
      getCompositeAggregationBuilder().aggregateAfter(compositeAggregationResult.afterKey());

      return compositeAggregationResult.getBuckets();
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to get next page of %s aggregation.", pathToAggregation.getLast());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        log.info(
            "Was not able to get next page of {} aggregation because at least one instance from {} does not exist.",
            pathToAggregation.getLast(),
            Arrays.toString(searchRequest.indices()));
        return Collections.emptyList();
      }
      throw e;
    }
  }

  private ParsedComposite extractCompositeAggregationResult(final SearchResponse searchResponse) {
    final ParsedComposite compositeAggregationResult;
    Aggregations aggregations = searchResponse.getAggregations();

    // find aggregation response
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      final SingleBucketAggregation agg = aggregations.get(pathToAggregation.get(i));
      aggregations = agg.getAggregations();
    }
    compositeAggregationResult = aggregations.get(pathToAggregation.getLast());
    return compositeAggregationResult;
  }

  private CompositeAggregationBuilder getCompositeAggregationBuilder() {
    Collection<AggregationBuilder> aggCol =
        searchRequest.source().aggregations().getAggregatorFactories();
    for (int i = 0; i < pathToAggregation.size() - 1; i++) {
      final int currentIndex = i;
      final AggregationBuilder currentAggregationFromPath =
          aggCol.stream()
              .filter(agg -> agg.getName().equals(pathToAggregation.get(currentIndex)))
              .findFirst()
              .orElseThrow(
                  () ->
                      new OptimizeRuntimeException(
                          String.format(
                              "Could not find aggregation [%s] in aggregation path.",
                              pathToAggregation.get(currentIndex))));
      aggCol = currentAggregationFromPath.getSubAggregations();
    }
    return (CompositeAggregationBuilder)
        aggCol.stream()
            .filter(agg -> agg.getName().equals(pathToAggregation.getLast()))
            .findFirst()
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        String.format(
                            "Could not find composite aggregation [%s] in aggregation path.",
                            pathToAggregation.getLast())));
  }

  public ElasticsearchCompositeAggregationScroller setSearchRequest(
      final SearchRequest searchRequest) {
    this.searchRequest = searchRequest;
    return this;
  }

  public ElasticsearchCompositeAggregationScroller setEsClient(
      final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public ElasticsearchCompositeAggregationScroller setCompositeBucketConsumer(
      final Consumer<ParsedComposite.ParsedBucket> compositeBucketConsumer) {
    this.compositeBucketConsumer = compositeBucketConsumer;
    return this;
  }

  /**
   * In order to be able to access the composite aggregation the scroller needs to know where to
   * find the composite aggregation. The path can be stated in this method:
   *
   * <p>Example: Aggregation: nested("fooNested",..).subAggregation(composite("myComposite")..)
   * Respective call: setPathToAggregation("fooNested", "myComposite")
   *
   * @param pathToAggregation a path to where to find the composite aggregation
   * @return the scroller object
   */
  public ElasticsearchCompositeAggregationScroller setPathToAggregation(
      final String... pathToAggregation) {
    this.pathToAggregation = new LinkedList<>(Arrays.asList(pathToAggregation));
    return this;
  }
}
