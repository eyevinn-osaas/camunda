/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.schema.SchemaTestUtil.validateMappings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.exporter.clients.elasticsearch.ElasticsearchClientFactory;
import io.camunda.exporter.config.ElasticsearchExporterConfiguration;
import io.camunda.exporter.entities.UserEntity;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This is a smoke test to verify that the exporter can connect to an Elasticsearch instance and
 * export records using the configured handlers.
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class CamundaExporterIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ExporterTestController controller = new ExporterTestController();
  private final ProtocolFactory factory = new ProtocolFactory();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  private ElasticsearchClient testClient;

  @BeforeAll
  public void beforeAll() {
    config.elasticsearch.setUrl(CONTAINER.getHttpHostAddress());
    config.elasticsearch.setIndexPrefix("");
    config.bulk.setSize(1); // force flushing on the first record

    testClient = ElasticsearchClientFactory.INSTANCE.create(config.elasticsearch);
  }

  @AfterAll
  void afterAll() throws IOException {
    testClient._transport().close();
  }

  @BeforeEach
  void beforeEach() throws IOException {
    testClient.indices().delete(req -> req.index("*"));
    testClient.indices().deleteIndexTemplate(req -> req.name("*"));

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "template_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    index = SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings.json");
  }

  private Context getContext() {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
  }

  @Test
  void shouldExportUserRecord() throws IOException {
    // given
    final var context = getContext();
    final var exporter = new CamundaExporter();
    exporter.configure(context);
    exporter.open(controller);
    final Record<UserRecordValue> record = factory.generateRecord(ValueType.USER);

    // when
    exporter.export(record);

    // then
    final String id = String.valueOf(record.getKey());
    final var response = testClient.get(b -> b.id(id).index("users"), UserEntity.class);
    assertThat(response)
        .extracting(GetResponse::index, GetResponse::id)
        .containsExactly("users", id);

    assertThat(response.source())
        .describedAs("User entity is updated correctly from the user record")
        .extracting(UserEntity::getEmail, UserEntity::getName, UserEntity::getUsername)
        .containsExactly(
            record.getValue().getEmail(),
            record.getValue().getName(),
            record.getValue().getUsername());
  }

  @Test
  void shouldCreateAllSchemasIfCreateEnabled() throws IOException {
    config.elasticsearch.setCreateSchema(true);

    final var exporter =
        new CamundaExporter(mockResourceProvider(Set.of(index), Set.of(indexTemplate)));

    final var context = getContext();
    exporter.configure(context);
    exporter.open(controller);

    final var indices = testClient.indices().get(req -> req.index("*"));
    final var indexTemplates =
        testClient.indices().getIndexTemplate(req -> req.name("template_name"));

    validateMappings(
        Objects.requireNonNull(indices.result().get(index.getFullQualifiedName()).mappings()),
        "mappings.json");
    validateMappings(
        indexTemplates.indexTemplates().get(0).indexTemplate().template().mappings(),
        "mappings.json");
  }

  @Test
  void shouldUpdateSchemasCorrectlyIfCreateEnabled() throws IOException {
    config.elasticsearch.setCreateSchema(true);

    final var exporter =
        new CamundaExporter(mockResourceProvider(Set.of(index), Set.of(indexTemplate)));
    final var context = getContext();
    exporter.configure(context);
    exporter.open(controller);

    when(index.getMappingsClasspathFilename()).thenReturn("mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("mappings-added-property.json");

    exporter.open(controller);
    final var indices = testClient.indices().get(req -> req.index("*"));
    final var indexTemplates =
        testClient.indices().getIndexTemplate(req -> req.name("template_name"));

    validateMappings(
        indices.result().get(index.getFullQualifiedName()).mappings(),
        "mappings-added-property.json");
    validateMappings(
        indexTemplates.indexTemplates().getFirst().indexTemplate().template().mappings(),
        "mappings-added-property.json");
  }

  @Test
  void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas() throws IOException {
    config.elasticsearch.setCreateSchema(true);
    final var resourceProvider = mockResourceProvider(Set.of(index), Set.of(indexTemplate));
    final var exporter = new CamundaExporter(resourceProvider);
    final var context = getContext();
    exporter.configure(context);
    exporter.open(controller);

    final var newIndex =
        SchemaTestUtil.mockIndex(
            "new_index_qualified", "new_alias", "new_index", "mappings-added-property.json");
    final var newIndexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "new_template_name",
            "new_test*",
            "new_template_alias",
            Collections.emptyList(),
            "new_template_name",
            "mappings-added-property.json");

    when(resourceProvider.getIndexDescriptors()).thenReturn(Set.of(index, newIndex));
    when(resourceProvider.getIndexTemplateDescriptors())
        .thenReturn(Set.of(indexTemplate, newIndexTemplate));

    exporter.open(controller);

    final var indices = testClient.indices().get(req -> req.index("*"));
    final var indexTemplates = testClient.indices().getIndexTemplate(req -> req.name("*"));

    validateMappings(
        indices.result().get(newIndex.getFullQualifiedName()).mappings(),
        "mappings-added-property.json");
    validateMappings(
        indexTemplates.indexTemplates().stream()
            .filter(template -> template.name().equals("new_template_name"))
            .findFirst()
            .orElseThrow()
            .indexTemplate()
            .template()
            .mappings(),
        "mappings-added-property.json");
  }

  @Test
  void shouldNotPutAnySchemasIfCreatedDisabled() throws IOException {
    config.elasticsearch.setCreateSchema(false);

    final var exporter =
        new CamundaExporter(mockResourceProvider(Set.of(index), Set.of(indexTemplate)));
    final var context = getContext();
    exporter.configure(context);
    exporter.open(controller);

    final var indices = testClient.indices().get(req -> req.index("*"));
    final var indexTemplates =
        testClient.indices().getIndexTemplate(req -> req.name("template_name*"));

    assertThat(indices.result().size()).isEqualTo(0);
    assertThat(indexTemplates.indexTemplates().size()).isEqualTo(0);
  }

  private ExporterResourceProvider mockResourceProvider(
      final Set<IndexDescriptor> indexDescriptors,
      final Set<IndexTemplateDescriptor> templateDescriptors) {
    final var provider = mock(ExporterResourceProvider.class);
    when(provider.getIndexDescriptors()).thenReturn(indexDescriptors);
    when(provider.getIndexTemplateDescriptors()).thenReturn(templateDescriptors);

    return provider;
  }
}
