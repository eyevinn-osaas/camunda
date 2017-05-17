import {dispatchAction, includes} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction, createLoadingDiagramErrorAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction, createLoadingHeatmapErrorAction,
        createLoadingTargetValueAction, createLoadingTargetValueResultAction, createLoadingTargetValueErrorAction
} from './diagram';
import {getFilterQuery} from './query';
import {getLastRoute} from 'router';
import {addNotification} from 'notifications';

const viewHeatmapEndpoints = {
  branch_analysis: 'frequency',
  frequency: 'frequency',
  duration: 'duration',
  target_value: 'duration'
};

export function loadData({query, view}) {
  const params = {
    processDefinitionId: getDefinitionId(),
    filter: getFilterQuery(query)
  };

  if (areParamsValid(params)) {
    if (includes(['duration', 'frequency', 'branch_analysis', 'target_value'], view)) {
      loadHeatmap(view, params);
    }
    if (view === 'target_value') {
      loadTargetValue(params.processDefinitionId);
    }
  }
}

function areParamsValid({processDefinitionId}) {
  return !!processDefinitionId;
}

export function loadTargetValue(definition) {
  dispatchAction(createLoadingTargetValueAction());
  get(`/api/process-definition/${definition}/heatmap/duration/target-value-comparison`)
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingTargetValueResultAction(result.targetValues));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load target value data',
        isError: true
      });
      dispatchAction(createLoadingTargetValueErrorAction(err));
    });
}

export function loadHeatmap(view, filter) {
  dispatchAction(createLoadingHeatmapAction());
  post(`/api/process-definition/heatmap/${viewHeatmapEndpoints[view]}`, filter)
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingHeatmapResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load heatmap data',
        isError: true
      });
      dispatchAction(createLoadingHeatmapErrorAction(err));
    });
}

export function loadDiagram(processDefinitionId = getDefinitionId()) {
  dispatchAction(createLoadingDiagramAction());
  get('/api/process-definition/' + processDefinitionId + '/xml')
    .then(response => response.text())
    .then(result => {
      dispatchAction(createLoadingDiagramResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load diagram',
        isError: true
      });
      dispatchAction(createLoadingDiagramErrorAction(err));
    });
}

export function getDefinitionId() {
  const {params: {definition}} = getLastRoute();

  return definition;
}
