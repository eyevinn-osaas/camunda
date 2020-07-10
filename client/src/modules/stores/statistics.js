/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';

const DEFAULT_STATE = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: false,
  isFailed: false,
};

class Statistics {
  state = {...DEFAULT_STATE};

  fetchStatistics = async () => {
    const {coreStatistics} = await fetchWorkflowCoreStatistics();
    if (coreStatistics.error) {
      this.setError();
    } else {
      this.setStatistics(coreStatistics);
    }
  };

  setError() {
    this.state.isLoaded = true;
    this.state.isFailed = true;
  }

  setStatistics = (coreStatistics) => {
    const {running, active, withIncidents} = coreStatistics;
    this.state = {
      running,
      active,
      withIncidents,
    };

    this.state.isLoaded = true;
    this.state.isFailed = false;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(Statistics, {
  state: observable,
  setError: action,
  setStatistics: action,
  reset: action,
});

export const statistics = new Statistics();
