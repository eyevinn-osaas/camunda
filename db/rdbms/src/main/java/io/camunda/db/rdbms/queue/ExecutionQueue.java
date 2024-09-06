/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.queue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionQueue {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutionQueue.class);

  private final SqlSessionFactory sessionFactory;
  private final LinkedList<ExecutionListener> executionListeners = new LinkedList<>();

  public ExecutionQueue(SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  private final List<QueueItem> queue = new ArrayList<>();

  public void executeInQueue(QueueItem entry) {
    LOG.debug("Added entry to queue: {}", entry);
    queue.add(entry);
    checkQueueForFlush();
  }

  public void registerExecutionListener(ExecutionListener listener) {
    this.executionListeners.add(listener);
  }

  public void flush() {
    if (queue.isEmpty()) {
      LOG.trace("Flushing empty execution queue");
      return;
    }
    LOG.debug("Flushing execution queue with {} items", queue.size());
    var session = sessionFactory.openSession();

    try {
      long lastPosition = -1;
      while (!queue.isEmpty()) {
        var entry = queue.getFirst();
        LOG.trace("Executing entry: {}", entry);
        session.update(entry.statementId(), entry.parameter());
        lastPosition = entry.eventPosition();
        queue.removeFirst();
      }

      for (var listener : executionListeners) {
        listener.onSuccess(lastPosition);
      }
      session.commit();
    } catch (Exception e) {
      LOG.error("Error while executing queue", e);
      session.rollback();
    } finally {
      session.close();
    }
  }

  private void checkQueueForFlush() {
    LOG.trace("Checking if queue is flushed. Queue size: {}", queue.size());
    if (queue.size() > 5) {
      flush();
    }
  }
}
