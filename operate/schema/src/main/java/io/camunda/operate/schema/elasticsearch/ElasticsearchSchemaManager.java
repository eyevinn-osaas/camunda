/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indexlifecycle.DeleteAction;
import org.elasticsearch.client.indexlifecycle.LifecycleAction;
import org.elasticsearch.client.indexlifecycle.LifecyclePolicy;
import org.elasticsearch.client.indexlifecycle.Phase;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager implements SchemaManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";
  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;
  @Autowired protected OperateProperties operateProperties;
  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Override
  public void createSchema() {
    if (operateProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public void checkAndUpdateIndices() {
    LOGGER.info("Updating Indices with currently-configured number of replicas...");
    final String currentConfigNumberOfReplicas =
        String.valueOf(operateProperties.getElasticsearch().getNumberOfReplicas());
    indexDescriptors.forEach(
        indexDescriptor -> {
          final String index = indexDescriptor.getIndexName();
          final Map<String, String> indexSettings = getIndexSettingsFor(index, NUMBERS_OF_REPLICA);
          final String currentIndexNumberOfReplicas = indexSettings.get(NUMBERS_OF_REPLICA);
          if (currentIndexNumberOfReplicas == null
              || !currentIndexNumberOfReplicas.equals(currentConfigNumberOfReplicas)) {
            indexSettings.put(NUMBERS_OF_REPLICA, currentConfigNumberOfReplicas);
            final boolean success = setIndexSettingsFor(indexSettings, index);
            if (success) {
              LOGGER.info("Successfully updated number of replicas for index {}", index);
            } else {
              LOGGER.warn("Failed to update number of replicas for index {}", index);
            }
          }
        });
  }

  @Override
  public boolean setIndexSettingsFor(final Map<String, ?> settings, final String indexPattern) {
    return retryElasticsearchClient.setIndexSettingsFor(
        Settings.builder().loadFromMap(settings).build(), indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(final String indexName, final String defaultValue) {
    return retryElasticsearchClient.getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    return retryElasticsearchClient.getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(final String indexPattern) {
    retryElasticsearchClient.refresh(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    return retryElasticsearchClient.isHealthy();
  }

  @Override
  public Set<String> getIndexNames(final String indexPattern) {
    return retryElasticsearchClient.getIndexNames(indexPattern);
  }

  @Override
  public Set<String> getAliasesNames(final String indexPattern) {
    return retryElasticsearchClient.getAliasesNames(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(final String... indexPatterns) {
    return retryElasticsearchClient.getNumberOfDocumentsFor(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(final String indexPattern) {
    return retryElasticsearchClient.deleteIndicesFor(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(final String deleteTemplatePattern) {
    return retryElasticsearchClient.deleteTemplatesFor(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(final String pipelineName) {
    retryElasticsearchClient.removePipeline(pipelineName);
  }

  @Override
  public boolean addPipeline(final String name, final String pipelineDefinition) {
    return retryElasticsearchClient.addPipeline(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(final String indexName, final String... fields) {
    return retryElasticsearchClient.getIndexSettingsFor(indexName, fields);
  }

  @Override
  public String getIndexPrefix() {
    return operateProperties.getElasticsearch().getIndexPrefix();
  }

  private String settingsTemplateName() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  private Settings getDefaultIndexSettings() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
  }

  private Settings getIndexSettings(final String indexName) {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final var shards =
        elsConfig
            .getNumberOfShardsForIndices()
            .getOrDefault(indexName, elsConfig.getNumberOfShards());
    final var replicas =
        elsConfig
            .getNumberOfReplicasForIndices()
            .getOrDefault(indexName, elsConfig.getNumberOfReplicas());
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, shards)
        .put(NUMBER_OF_REPLICAS, replicas)
        .build();
  }

  private void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String settingsTemplate = settingsTemplateName();
    LOGGER.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final Settings settings = getDefaultIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request =
        new PutComponentTemplateRequest()
            .name(settingsTemplate)
            .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  private void createIndexLifeCycles() {
    final TimeValue timeValue =
        TimeValue.parseTimeValue(
            operateProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices(),
            "IndexLifeCycle " + INDEX_LIFECYCLE_NAME);
    LOGGER.info(
        "Create Index Lifecycle {} for min age of {} ",
        OPERATE_DELETE_ARCHIVED_INDICES,
        timeValue.getStringRep());
    final Map<String, Phase> phases = new HashMap<>();
    final Map<String, LifecycleAction> deleteActions =
        Collections.singletonMap(DeleteAction.NAME, new DeleteAction());
    phases.put(DELETE_PHASE, new Phase(DELETE_PHASE, timeValue, deleteActions));

    final LifecyclePolicy policy = new LifecyclePolicy(OPERATE_DELETE_ARCHIVED_INDICES, phases);
    final PutLifecyclePolicyRequest request = new PutLifecyclePolicyRequest(policy);
    retryElasticsearchClient.putLifeCyclePolicy(request);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format(
            "/schema/elasticsearch/create/index/operate-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = readJSONFileToMap(indexFilename);
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(indexDescriptor.getIndexName())),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final Template template = getTemplateFrom(templateDescriptor);
    final ComposableIndexTemplate composableTemplate =
        new ComposableIndexTemplate.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .componentTemplates(List.of(settingsTemplateName()))
            .build();
    putIndexTemplate(
        new PutComposableIndexTemplateRequest()
            .name(templateDescriptor.getTemplateName())
            .indexTemplate(composableTemplate));
    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    final var createIndexRequest =
        new CreateIndexRequest(indexName)
            .aliases(Set.of(new Alias(templateDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings(templateDescriptor.getIndexName()));
    createIndex(createIndexRequest, indexName);
  }

  private void overrideTemplateSettings(
      final Map<String, Object> templateConfig, final TemplateDescriptor templateDescriptor) {
    final Settings indexSettings = getIndexSettings(templateDescriptor.getIndexName());
    final Map<String, Object> settings =
        (Map<String, Object>) templateConfig.getOrDefault("settings", new HashMap<>());
    final Map<String, Object> index =
        (Map<String, Object>) settings.getOrDefault("index", new HashMap<>());
    index.put("number_of_shards", indexSettings.get(NUMBER_OF_SHARDS));
    index.put("number_of_replicas", indexSettings.get(NUMBER_OF_REPLICAS));
    settings.put("index", index);
    templateConfig.put("settings", settings);
  }

  private Template getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format(
            "/schema/elasticsearch/create/template/operate-%s.json",
            templateDescriptor.getIndexName());
    // Easiest way to create Template from json file: create 'old' request ang retrieve needed info
    final Map<String, Object> templateConfig = readJSONFileToMap(templateFilename);
    overrideTemplateSettings(templateConfig, templateDescriptor);
    final PutIndexTemplateRequest ptr =
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases =
          Map.of(
              templateDescriptor.getAlias(),
              AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          String.format("Error in reading mappings for %s ", templateDescriptor.getTemplateName()),
          e);
    }
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (final InputStream inputStream =
        ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new OperateRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutComposableIndexTemplateRequest request) {
    final boolean created = retryElasticsearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }
}
