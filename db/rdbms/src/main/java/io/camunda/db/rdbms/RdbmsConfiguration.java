/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.db.rdbms.service.VariableRdbmsService;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
public class RdbmsConfiguration {

  @Bean
  public MultiTenantSpringLiquibase customerLiquibase(final DataSource dataSource) {
    final var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-support/changelog-master.xml");
    return moduleConfig;
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(final DataSource dataSource) throws Exception {
    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) throws Exception {
    final MapperFactoryBean<ProcessInstanceMapper> factoryBean = new MapperFactoryBean<>(
        ProcessInstanceMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(
      final SqlSessionFactory sqlSessionFactory) throws Exception {
    final MapperFactoryBean<VariableMapper> factoryBean = new MapperFactoryBean<>(
        VariableMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public ExecutionQueue executionQueue(final SqlSessionFactory sqlSessionFactory) {
    return new ExecutionQueue(sqlSessionFactory);
  }

  @Bean
  public VariableRdbmsService variableRdbmsService(
      final ExecutionQueue executionQueue,
      final VariableMapper variableMapper) {
    return new VariableRdbmsService(executionQueue, variableMapper);
  }

  @Bean
  public ProcessRdbmsService processRdbmsService(
      final ExecutionQueue executionQueue,
      final ProcessInstanceMapper processInstanceMapper) {
    return new ProcessRdbmsService(executionQueue, processInstanceMapper);
  }

  @Bean
  public RdbmsService rdbmsService(final ExecutionQueue executionQueue,
      final VariableRdbmsService variableRdbmsService,
      final ProcessRdbmsService processRdbmsService) {
    return new RdbmsService(executionQueue, processRdbmsService, variableRdbmsService);
  }

}
