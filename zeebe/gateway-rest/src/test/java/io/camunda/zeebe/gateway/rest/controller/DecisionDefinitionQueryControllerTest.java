/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.search.filter.DecisionDefinitionFilter;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.sort.DecisionDefinitionSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = DecisionDefinitionQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class DecisionDefinitionQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "tenantId": "t",
                  "decisionKey": 0,
                  "dmnDecisionId": "dId",
                  "dmnDecisionName": "name",
                  "version": 1,
                  "dmnDecisionRequirementsId": "drId",
                  "decisionRequirementsKey": 2
              }
          ],
          "page": {
              "totalItems": 1,
              "firstSortValues": [],
              "lastSortValues": [
                  "v"
              ]
          }
      }""";

  static final SearchQueryResult<DecisionDefinitionEntity> SEARCH_QUERY_RESULT =
      new Builder<DecisionDefinitionEntity>()
          .total(1L)
          .items(List.of(new DecisionDefinitionEntity("t", 0L, "dId", "name", 1, "drId", 2L)))
          .sortValues(new Object[] {"v"})
          .build();

  static final String DECISION_DEFINITIONS_SEARCH_URL = "/v2/exported/decision-definitions/search";

  @MockBean DecisionDefinitionServices decisionDefinitionServices;

  @BeforeEach
  void setupServices() {
    when(decisionDefinitionServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionDefinitionServices);
  }

  @Test
  void shouldSearchDecisionDefinitionsWithEmptyBody() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionDefinitionServices).search(new DecisionDefinitionQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithEmptyQuery() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionDefinitionServices).search(new DecisionDefinitionQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithAllFilters() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
          "filter":{
            "tenantId": "t",
            "decisionKey": 0,
            "dmnDecisionName": "name",
            "version": 1,
            "dmnDecisionRequirementsId": "drId",
            "decisionRequirementsKey": 2,
            "dmnDecisionId": "dId",
            "decisionRequirementsName": "drName",
            "decisionRequirementsVersion": 3
          }
        }""";

    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionDefinitionServices)
        .search(
            new DecisionDefinitionQuery.Builder()
                .filter(
                    new DecisionDefinitionFilter.Builder()
                        .tenantIds("t")
                        .decisionKeys(0L)
                        .dmnDecisionNames("name")
                        .versions(1)
                        .dmnDecisionRequirementsIds("drId")
                        .decisionRequirementsKeys(2L)
                        .dmnDecisionIds("dId")
                        .build())
                .build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithFullSorting() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
            "sort": [
                {
                    "field": "decisionKey",
                    "order": "asc"
                },
                {
                    "field": "dmnDecisionName",
                    "order": "desc"
                },
                {
                    "field": "version",
                    "order": "asc"
                },
                {
                     "field": "dmnDecisionId"
                },
                {
                     "field": "decisionRequirementsKey"
                },
                {
                     "field": "dmnDecisionRequirementsId"
                },
                {
                     "field": "tenantId"
                }
            ]
        }""";
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionDefinitionServices)
        .search(
            new DecisionDefinitionQuery.Builder()
                .sort(
                    new DecisionDefinitionSort.Builder()
                        .decisionKey()
                        .asc()
                        .dmnDecisionName()
                        .desc()
                        .version()
                        .asc()
                        .dmnDecisionId()
                        .asc()
                        .decisionRequirementsKey()
                        .asc()
                        .dmnDecisionRequirementsId()
                        .asc()
                        .tenantId()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldInvalidateDecisionDefinitionsSearchQueryWithBadSortField() {
    // given
    final var request =
        """
        {
            "sort": [
                {
                    "field": "unknownField",
                    "order": "asc"
                }
            ]
        }""";
    final var expectedResponse =
        String.format(
            """
        {
          "type": "about:blank",
          "title": "INVALID_ARGUMENT",
          "status": 400,
          "detail": "Unknown sortBy: unknownField.",
          "instance": "%s"
        }""",
            DECISION_DEFINITIONS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(decisionDefinitionServices, never()).search(any(DecisionDefinitionQuery.class));
  }
}
