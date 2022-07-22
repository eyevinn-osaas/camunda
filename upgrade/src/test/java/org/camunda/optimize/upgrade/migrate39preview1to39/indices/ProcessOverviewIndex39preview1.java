/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DYNAMIC_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROPERTIES_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_BOOLEAN;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_INTEGER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;

public class ProcessOverviewIndex39preview1 extends DefaultIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String PROCESS_DEFINITION_KEY = ProcessOverviewDto.Fields.processDefinitionKey;
  public static final String OWNER = ProcessOverviewDto.Fields.owner;
  public static final String DIGEST = ProcessOverviewDto.Fields.digest;
  public static final String ENABLED = ProcessDigestResponseDto.Fields.enabled;
  public static final String KPI_REPORT_RESULTS = ProcessDigestDto.Fields.kpiReportResults;
  public static final String INTERVAL_VALUE = AlertInterval.Fields.value;
  public static final String INTERVAL_UNIT = AlertInterval.Fields.unit;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(PROCESS_DEFINITION_KEY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(OWNER)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(DIGEST)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
          .startObject(PROPERTIES_PROPERTY_TYPE)
            .startObject("checkInterval")
              .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
               .startObject(PROPERTIES_PROPERTY_TYPE)
                .startObject(INTERVAL_VALUE)
                   .field(MAPPING_PROPERTY_TYPE, TYPE_INTEGER)
                .endObject()
                .startObject(INTERVAL_UNIT)
                  .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
                .endObject()
               .endObject()
             .endObject()
             .startObject(ENABLED)
              .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
             .endObject()
             .startObject(KPI_REPORT_RESULTS)
               .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
               .field(DYNAMIC_PROPERTY_TYPE, true)
             .endObject()
            .endObject()
        .endObject();
    // @formatter:on
  }
}
