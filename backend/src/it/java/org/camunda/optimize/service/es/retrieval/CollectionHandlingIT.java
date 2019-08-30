/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRole;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

public class CollectionHandlingIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void collectionIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCollection();

    // then
    GetRequest getRequest = new GetRequest(COLLECTION_INDEX_NAME, COLLECTION_INDEX_NAME, id);
    GetResponse getResponse = elasticSearchRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
  }

  @Test
  public void newCollectionIsCorrectlyInitialized() {
    // given
    String id = createNewCollection();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection = collections.get(0);
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is(DEFAULT_COLLECTION_NAME));
    assertThat(collection.getData().getEntities(), notNullValue());
    assertThat(collection.getData().getEntities().size(), is(0));
    assertThat(collection.getData().getConfiguration(), notNullValue());
    // author is automatically added as manager
    assertThat(collection.getData().getRoles(), notNullValue());
    assertThat(collection.getData().getRoles().size(), is(1));
    final CollectionRoleDto roleDto = collection.getData().getRoles().get(0);
    assertThat(roleDto.getId(), is(notNullValue()));
    assertThat(roleDto.getIdentity(), is(notNullValue()));
    assertThat(roleDto.getIdentity().getId(), is(DEFAULT_USERNAME));
    assertThat(roleDto.getIdentity().getType(), is(IdentityType.USER));
    assertThat(roleDto.getRole(), is(CollectionRole.MANAGER));
  }

  @Test
  public void returnEmptyListWhenNoCollectionIsDefined() {
    // given
    createNewSingleProcessReportInCollection(null);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(0));
  }

  @Test
  public void getResolvedCollection() {
    //given
    final String collectionId = createNewCollection();
    final String dashboardId = createNewDashboardInCollection(collectionId);
    final String reportId = createNewSingleProcessReportInCollection(collectionId);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ResolvedCollectionDefinitionDto collection = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(collectionId));
    assertThat(collection.getData().getEntities().size(), is(2));
    assertThat(
      collection.getData().getEntities().stream().map(CollectionEntity::getId).collect(Collectors.toList()),
      containsInAnyOrder(dashboardId, reportId)
    );
  }

  @Test
  public void updateCollection() {
    // given
    String id = createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    PartialCollectionUpdateDto collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("MyCollection");
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    final PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);


    // when
    updateCollectionRequest(id, collectionUpdate);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    final ResolvedCollectionDataDto resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);
    assertThat(resultCollectionData.getEntities().size(), is(0));
  }

  @Test
  public void updatePartialCollection() {
    // given
    String id = createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when (update only name)
    PartialCollectionUpdateDto collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("MyCollection");

    updateCollectionRequest(id, collectionUpdate);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));

    // when (update only configuration)
    collectionUpdate = new PartialCollectionUpdateDto();
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);

    updateCollectionRequest(id, collectionUpdate);
    collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    ResolvedCollectionDataDto resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);


    // when (again only update name)
    collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("TestNewCollection");

    updateCollectionRequest(id, collectionUpdate);
    collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("TestNewCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);
  }

  @Test
  public void singleProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewSingleProcessReportInCollection(collectionId);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    SingleProcessReportDefinitionDto report = (SingleProcessReportDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
  }

  @Test
  public void singleDecisionReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewSingleDecisionReportInCollection(collectionId);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    SingleDecisionReportDefinitionDto report = (SingleDecisionReportDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
  }

  @Test
  public void combinedProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewCombinedReportInCollection(collectionId);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    CombinedReportDefinitionDto report = (CombinedReportDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
  }

  @Test
  public void dashboardCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String dashboardId = createNewDashboardInCollection(collectionId);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    DashboardDefinitionDto dashboard = (DashboardDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(dashboard.getId(), is(dashboardId));
  }

  @Test
  public void singleProcessReportCanNotBeCreatedForInvalidCollection() {
    // given
    String invalidCollectionId = "invalidId";

    // when
    final Response createResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(invalidCollectionId)
      .execute();

    // then
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
  }

  @Test
  public void singleDecisionReportCanNotBeCreatedForInvalidCollection() {
    // given
    String invalidCollectionId = "invalidId";

    // when
    final Response createResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(invalidCollectionId)
      .execute();

    // then
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
  }

  @Test
  public void combinedProcessReportCanNotBeCreatedForInvalidCollection() {
    // given
    String invalidCollectionId = "invalidId";

    // when
    final Response createResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(invalidCollectionId)
      .execute();

    // then
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
  }

  @Test
  public void dashboardCanNotBeCreatedForInvalidCollection() {
    // given
    String invalidCollectionId = "invalidId";

    // when
    final Response createResponse = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(invalidCollectionId)
      .execute();

    // then
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
  }

  @Test
  public void collectionItemsAreOrderedByModificationDateDescending() {
    // given
    String collectionId = createNewCollection();
    String reportId1 = createNewSingleProcessReportInCollection(collectionId);
    String reportId2 = createNewSingleProcessReportInCollection(collectionId);
    String dashboardId = createNewDashboardInCollection(collectionId);

    updateReport(reportId1, new SingleProcessReportDefinitionDto());

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection = collections.get(0);
    assertThat(collection.getData().getEntities().get(0).getId(), is(reportId1));
    assertThat(collection.getData().getEntities().get(1).getId(), is(dashboardId));
    assertThat(collection.getData().getEntities().get(2).getId(), is(reportId2));
  }

  @Test
  public void doNotUpdateNullFieldsInCollection() {
    // given
    String id = createNewCollection();
    PartialCollectionUpdateDto collection = new PartialCollectionUpdateDto();

    // when
    updateCollectionRequest(id, collection);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getCreated(), is(notNullValue()));
    assertThat(storedCollection.getLastModified(), is(notNullValue()));
    assertThat(storedCollection.getLastModifier(), is(notNullValue()));
    assertThat(storedCollection.getName(), is(notNullValue()));
    assertThat(storedCollection.getOwner(), is(notNullValue()));
  }

  @Test
  public void resultListIsSortedByName() {
    // given
    String id1 = createNewCollection();
    String id2 = createNewCollection();

    PartialCollectionUpdateDto collection = new PartialCollectionUpdateDto();
    collection.setName("B_collection");
    updateCollectionRequest(id1, collection);
    collection.setName("A_collection");
    updateCollectionRequest(id2, collection);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(2));
    assertThat(collections.get(0).getId(), is(id2));
    assertThat(collections.get(1).getId(), is(id1));
  }

  @Test
  public void deletedReportsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String singleReportIdToDelete = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportIdToDelete = createNewCombinedReportInCollection(collectionId);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteReport(singleReportIdToDelete);
    deleteReport(combinedReportIdToDelete);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(1));
    assertThat(allResolvedCollections.get(0).getData().getEntities().size(), is(0));
  }

  @Test
  public void deletedDashboardsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String dashboardIdToDelete = createNewDashboardInCollection(collectionId);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteDashboard(dashboardIdToDelete);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(1));
    assertThat(allResolvedCollections.get(0).getData().getEntities().size(), is(0));
  }

  @Test
  public void entitiesAreDeletedOnCollectionDelete() {
    // given
    String collectionId = createNewCollection();
    String singleReportId = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportId = createNewCombinedReportInCollection(collectionId);
    String dashboardId = createNewDashboardInCollection(collectionId);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteCollection(collectionId);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(0));

    assertDashboardIsDeleted(dashboardId);
    assertReportIsDeleted(singleReportId);
    assertReportIsDeleted(combinedReportId);
  }

  @Test
  public void addDefinitionScopeEntry() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = new CollectionScopeEntryDto();
    entry.setDefinitionKey("_KEY_");
    entry.setDefinitionType("PROCESS");
    entry.setTenants(Collections.singletonList(null));
    entry.setVersions(Collections.singletonList("ALL"));

    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(204);


    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size(), is(1));
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getId(), is("PROCESS:_KEY_"));
  }

  @Test
  public void addConflictedScopeDefinition() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = new CollectionScopeEntryDto();
    entry.setDefinitionKey("_KEY_");
    entry.setDefinitionType("PROCESS");
    entry.setTenants(Collections.singletonList(null));
    entry.setVersions(Collections.singletonList("ALL"));

    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(204);


    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(409);
  }

  @Test
  public void removeScopeDefinitionWithDependingReport() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = new CollectionScopeEntryDto();
    entry.setDefinitionKey("_KEY_");
    entry.setDefinitionType("PROCESS");
    entry.setTenants(Collections.singletonList(null));
    entry.setVersions(Collections.singletonList("ALL"));

    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(204);


    String reportId = createNewSingleProcessReportInCollection(collectionId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.getData().setProcessDefinitionKey("_KEY_");

    updateReport(reportId, report);

    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyReportRequest(reportId, collectionId)
      .execute(IdDto.class, 200);

    ConflictResponseDto conflictResponseDto = embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(ConflictResponseDto.class, 409);

    assertThat(conflictResponseDto.getConflictedItems().stream().anyMatch(i -> i.getId().equals(copyId.getId())), is(true));
  }

  @Test
  public void removeScopeDefinition() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = new CollectionScopeEntryDto();
    entry.setDefinitionKey("_KEY_");
    entry.setDefinitionType("PROCESS");
    entry.setTenants(Collections.singletonList(null));
    entry.setVersions(Collections.singletonList("ALL"));

    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(204);

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size(), is(1));

     embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(204);

    collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size(), is(0));
  }

  @Test
  public void removeNotExistingScopeDefinition() {
    String collectionId = createNewCollection();

    embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(404);
  }

  @Test
  public void editDefinitionScopeEntry() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = new CollectionScopeEntryDto();
    entry.setDefinitionKey("_KEY_");
    entry.setDefinitionType("PROCESS");
    entry.setTenants(Collections.singletonList(null));
    entry.setVersions(Collections.singletonList("ALL"));

    embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(204);

    entry.setVersions(Collections.singletonList("1"));
    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, new CollectionScopeEntryUpdateDto(entry), "PROCESS:_KEY_")
      .execute(204);

    elasticSearchRule.refreshAllOptimizeIndices();

    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().size(), is(1));
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().get(0), is("1"));
  }

  private void assertReportIsDeleted(final String singleReportIdToDelete) {
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private void assertDashboardIsDeleted(final String dashboardIdToDelete) {
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private String createNewSingleProcessReportInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleDecisionReportInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewDashboardInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCombinedReportInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response deleteCollection(String id) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildDeleteCollectionRequest(id, true)
      .execute();
  }

  private void deleteReport(String reportId) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private void deleteDashboard(String dashboardId) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewCollection() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateCollectionRequest(String id, PartialCollectionUpdateDto renameCollection) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(id, renameCollection)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateReportResponse(String id, SingleProcessReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
  }

  private List<ResolvedCollectionDefinitionDto> getAllResolvedCollections() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(ResolvedCollectionDefinitionDto.class, 200);
  }


}
