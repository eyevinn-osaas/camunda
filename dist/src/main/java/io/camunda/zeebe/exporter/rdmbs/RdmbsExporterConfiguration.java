/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.rdmbs;

import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RdmbsExporterConfiguration {

  @Bean
  public MultiTenantSpringLiquibase customerLiquibase(DataSource dataSource) {
    var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");
    return moduleConfig;
  }
}
