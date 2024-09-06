/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentStore;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

public class DocumentStoreRegistry {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DocumentStoreRegistry.class);

  private static final String GCP_STORE_BUCKET_NAME_VARIABLE = "CAMUNDA_DOCUMENT_STORE_GCP_BUCKET";

  private static final String STORE_ID_GCP = "gcp";
  private static final String STORE_ID_IN_MEMORY = "in-memory";

  private final Map<String, DocumentStore> stores = new HashMap<>();

  public DocumentStoreRegistry() {
    final String gcpBucketName = System.getenv(GCP_STORE_BUCKET_NAME_VARIABLE);
    if (gcpBucketName != null) {
      stores.put(STORE_ID_GCP, new GcpDocumentStore(gcpBucketName));
    }
    LOG.warn("No GCP bucket name provided, using in-memory document store");
    stores.put(STORE_ID_IN_MEMORY, new InMemoryDocumentStore());
  }

  public DocumentStoreInstance getDocumentStore(final String id) {
    final DocumentStore store = stores.get(id);
    if (store == null) {
      throw new IllegalArgumentException("No such document store: " + id);
    }
    return new DocumentStoreInstance(id, store);
  }

  public DocumentStoreInstance getDefaultDocumentStore() {
    if (stores.containsKey(STORE_ID_GCP)) {
      return new DocumentStoreInstance(STORE_ID_GCP, stores.get(STORE_ID_GCP));
    }
    return new DocumentStoreInstance(STORE_ID_IN_MEMORY, stores.get(STORE_ID_IN_MEMORY));
  }

  public record DocumentStoreInstance(String storeId, DocumentStore store) {}
}
