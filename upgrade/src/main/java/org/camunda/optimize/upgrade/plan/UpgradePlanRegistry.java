/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.camunda.optimize.upgrade.plan.factories.UpgradePlanFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UpgradePlanRegistry {

  private final Map<String, UpgradePlan> upgradePlans = new HashMap<>();

  public UpgradePlanRegistry(final OptimizeElasticsearchClient esClient) {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(UpgradePlanFactory.class.getPackage().getName())
      .scan()) {
      scanResult.getClassesImplementing(UpgradePlanFactory.class.getName())
        .forEach(upgradePlanFactoryClass -> {
          try {
            final UpgradePlanFactory planFactory = (UpgradePlanFactory) upgradePlanFactoryClass.loadClass()
              .getConstructor().newInstance();
            final UpgradePlan upgradePlan = planFactory.createUpgradePlan(esClient);
            if (planFactory instanceof CurrentVersionNoOperationUpgradePlanFactory) {
              // The no operation  upgrade plan will only get added if there is not a custom plan yet
              upgradePlans.putIfAbsent(upgradePlan.getToVersion(), upgradePlan);
            } else {
              // specific upgrade plans always overwrite any preexisting entries
              // (e.g. if no operation default upgrade plan was added first)
              upgradePlans.put(upgradePlan.getToVersion(), upgradePlan);
            }
          } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Could not instantiate {}, will skip this factory.", upgradePlanFactoryClass.getName());
            throw new UpgradeRuntimeException("Failed to instantiate upgrade plan: " + upgradePlanFactoryClass.getName());
          }
        });
    }
  }

  public UpgradePlan getUpgradePlanForTargetVersion(final String targetVersion) {
    return upgradePlans.get(targetVersion);
  }

}
