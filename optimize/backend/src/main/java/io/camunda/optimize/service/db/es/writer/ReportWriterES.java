/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DESCRIPTION;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ReportWriterES implements ReportWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ReportWriterES.class);
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;

  public ReportWriterES(
      final ObjectMapper objectMapper, final OptimizeElasticsearchClient esClient) {
    this.objectMapper = objectMapper;
    this.esClient = esClient;
  }

  @Override
  public IdResponseDto createNewCombinedReport(
      final String userId,
      final CombinedReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId) {
    if (userId == null) {
      throw new RuntimeException("userId cannot be null");
    }
    if (reportData == null) {
      throw new RuntimeException("reportData cannot be null");
    }
    if (reportName == null) {
      throw new RuntimeException("reportName cannot be null");
    }

    log.debug("Writing new combined report to Elasticsearch");
    final String id = IdGenerator.getNextId();
    final CombinedReportDefinitionRequestDto reportDefinitionDto =
        new CombinedReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexRequest request =
          new IndexRequest(COMBINED_REPORT_INDEX_NAME)
              .id(id)
              .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        final String message = "Could not write report to Elasticsearch. ";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert combined report.!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public IdResponseDto createNewSingleProcessReport(
      final String userId,
      final ProcessReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId) {
    if (reportData == null) {
      throw new RuntimeException("reportData cannot be null");
    }
    if (reportName == null) {
      throw new RuntimeException("reportName cannot be null");
    }

    log.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexRequest request =
          new IndexRequest(SINGLE_PROCESS_REPORT_INDEX_NAME)
              .id(id)
              .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        final String message = "Could not write single process report to Elasticsearch.";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Single process report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert single process report.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public IdResponseDto createNewSingleDecisionReport(
      final String userId,
      final DecisionReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId) {
    if (userId == null) {
      throw new RuntimeException("userId cannot be null");
    }
    if (reportData == null) {
      throw new RuntimeException("reportData cannot be null");
    }
    if (reportName == null) {
      throw new RuntimeException("reportName cannot be null");
    }

    log.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleDecisionReportDefinitionRequestDto reportDefinitionDto =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexRequest request =
          new IndexRequest(SINGLE_DECISION_REPORT_INDEX_NAME)
              .id(id)
              .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        final String message = "Could not write single decision report to Elasticsearch.";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Single decision report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert single decision report.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void updateSingleDecisionReport(
      final SingleDecisionReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  @Override
  public void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, COMBINED_REPORT_INDEX_NAME);
  }

  @Override
  public void updateProcessDefinitionXmlForProcessReportsWithKey(
      final String definitionKey, final String definitionXml) {
    final String updateItem = String.format("reports with definitionKey [%s]", definitionKey);
    log.debug("Updating definition XML in {} in Elasticsearch", updateItem);

    final Script updateDefinitionXmlScript =
        new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            // this script is deliberately not updating the modified date as this is no user
            // operation
            "ctx._source.data.configuration.xml = params.newXml;",
            Collections.singletonMap("newXml", definitionXml));

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        updateItem,
        updateDefinitionXmlScript,
        termQuery(PROCESS_DEFINITION_PROPERTY, definitionKey),
        SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteAllManagementReports() {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        QueryBuilders.termQuery(DATA + "." + MANAGEMENT_REPORT, true),
        "all management reports",
        true,
        SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteSingleReport(final String reportId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        idsQuery().addIds(reportId),
        String.format("single report with ID [%s]", reportId),
        true,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  @Override
  public void removeSingleReportFromCombinedReports(final String reportId) {
    final String updateItemName = String.format("report with ID [%s]", reportId);
    log.info("Removing {} from combined report.", updateItemName);

    final Script removeReportIdFromCombinedReportsScript =
        new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            "def reports = ctx._source.data.reports;"
                + "if(reports != null) {"
                + "  reports.removeIf(r -> r.id.equals(params.idToRemove));"
                + "}",
            Collections.singletonMap("idToRemove", reportId));

    final NestedQueryBuilder query =
        nestedQuery(
            DATA,
            nestedQuery(
                String.join(".", DATA, REPORTS),
                termQuery(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), reportId),
                ScoreMode.None),
            ScoreMode.None);

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient,
        updateItemName,
        removeReportIdFromCombinedReportsScript,
        query,
        COMBINED_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteCombinedReport(final String reportId) {
    log.debug("Deleting combined report with id [{}]", reportId);

    final DeleteRequest request =
        new DeleteRequest(COMBINED_REPORT_INDEX_NAME).id(reportId).setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not delete combined report with id [%s].", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      final String message =
          String.format(
              "Could not delete combined process report with id [%s]. "
                  + "Combined process report does not exist."
                  + "Maybe it was already deleted by someone else?",
              reportId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteAllReportsOfCollection(final String collectionId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        QueryBuilders.termQuery(COLLECTION_ID, collectionId),
        String.format("all reports of collection with collectionId [%s]", collectionId),
        true,
        COMBINED_REPORT_INDEX_NAME,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  private void updateReport(final ReportDefinitionUpdateDto updatedReport, final String indexName) {
    log.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    try {
      final Map<String, Object> updateParams =
          DatabaseWriterUtil.createFieldUpdateScriptParams(
              UPDATABLE_FIELDS, updatedReport, objectMapper);
      // We always update the description, even if the new value is null
      updateParams.put(DESCRIPTION, updatedReport.getDescription());
      final Script updateScript =
          createDefaultScriptWithPrimitiveParams(
              DatabaseWriterUtil.createUpdateFieldsScript(updateParams.keySet()), updateParams);
      final UpdateRequest request =
          new UpdateRequest()
              .index(indexName)
              .id(updatedReport.getId())
              .script(updateScript)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse updateResponse = esClient.update(request);
      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error(
            "Was not able to update report with id [{}] and name [{}].",
            updatedReport.getId(),
            updatedReport.getName());
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update report with id [%s].", updatedReport.getId());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final DocumentMissingException e) {
      final String errorMessage =
          String.format(
              "Was not able to update report with id [%s] and name [%s]. Report does not exist!",
              updatedReport.getId(), updatedReport.getName());
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }
}
