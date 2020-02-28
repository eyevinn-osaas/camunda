/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom27To30;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;

public class UpgradeEventProcessMappingsIT extends AbstractUpgradeIT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      EVENT_INDEX_V1,
      EVENT_PROCESS_MAPPING_INDEX_V1,
      EVENT_PROCESS_PUBLISH_STATE_INDEX_V1,
      TIMESTAMP_BASED_IMPORT_INDEX_V2
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/event_process_mappings/27-event-process-mappings-bulk");
  }

  @Test
  public void addEventSourcesListFieldWithDefaultExternalEventsEntry() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<EventProcessMappingDto> eventProcessMappingRestDtos = getEventProcessMappings();
    assertThat(eventProcessMappingRestDtos.size()).isEqualTo(2);
    assertThat(eventProcessMappingRestDtos)
      .extracting(EventProcessMappingDto::getEventSources)
      .allSatisfy(eventSourceEntryDtos -> {
        assertThat(eventSourceEntryDtos)
          .hasSize(1)
          .allSatisfy(eventSourceEntryDto -> {
            assertThat(eventSourceEntryDto.getId()).isNotBlank();
            assertThat(eventSourceEntryDto.getType()).isEqualTo(EventSourceType.EXTERNAL);
            assertThat(eventSourceEntryDto.getEventScope()).isEqualTo(EventScopeType.ALL);
          });
      });
  }

  @Test
  public void initializeRolesField() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<EventProcessMappingDto> eventProcessMappingRestDtos = getEventProcessMappings();
    assertThat(eventProcessMappingRestDtos)
      .hasSize(2)
      .extracting(EventProcessMappingDto::getRoles)
      .flatExtracting(eventProcessRoleDtos -> eventProcessRoleDtos)
      .allSatisfy(eventProcessRoleDto -> {
        assertThat(eventProcessRoleDto.getId()).isEqualTo("USER:demo");
        assertThat(eventProcessRoleDto.getIdentity())
          .isEqualTo(new IdentityDto("demo", IdentityType.USER));
      });
  }

  @SneakyThrows
  private List<EventProcessMappingDto> getEventProcessMappings() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(EVENT_PROCESS_MAPPING_INDEX_NAME).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), EventProcessMappingDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }
}
