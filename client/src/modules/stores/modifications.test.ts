/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from './modifications';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {flowNodeStatesStore} from './flowNodeStates';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  createAddVariableModification,
  createEditVariableModification,
} from 'modules/mocks/modifications';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';

describe('stores/modifications', () => {
  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStatisticsStore.reset();
    flowNodeStatesStore.reset();
  });

  it('should enable/disable modification mode', async () => {
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    modificationsStore.disableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    modificationsStore.startApplyingModifications();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });

  it('should add/remove flow node modifications', async () => {
    const uniqueID = generateUniqueID();
    const uniqueIDForMove = generateUniqueID();
    expect(modificationsStore.state.modifications).toEqual([]);
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(3);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
      {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    ]);

    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'non-existing-flow-node', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '4', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '2', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '2', name: ''},
      operation: 'CANCEL_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(2);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '1', name: ''},
      operation: 'ADD_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      scopeId: uniqueID,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(1);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    ]);
  });

  it('should add/remove variable modifications', async () => {
    expect(modificationsStore.state.modifications).toEqual([]);
    createAddVariableModification({
      id: '1',
      scopeId: '1',
      flowNodeName: 'flow-node-1',
      name: 'variable1',
      value: 'variable1-newValue',
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);
    createEditVariableModification({
      name: 'variable1',
      oldValue: 'variable2-oldValue',
      newValue: 'variable2-newValue',
      flowNodeName: 'flow-node-2',
      scopeId: '2',
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      'non-existing-variable',
      'variable1',
      'EDIT_VARIABLE',
      'variables'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      'non-existing-variable-name',
      'ADD_VARIABLE',
      'variables'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      '1',
      'ADD_VARIABLE',
      'variables'
    );
    expect(modificationsStore.state.modifications.length).toEqual(1);

    expect(modificationsStore.state.lastRemovedModification).toEqual({
      modification: {
        payload: {
          flowNodeName: 'flow-node-1',
          id: '1',
          name: 'variable1',
          newValue: 'variable1-newValue',
          operation: 'ADD_VARIABLE',
          scopeId: '1',
        },
        type: 'variable',
      },
      source: 'variables',
    });
    expect(modificationsStore.state.modifications).toEqual([
      {
        payload: {
          flowNodeName: 'flow-node-2',
          id: 'variable1',
          scopeId: '2',
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'EDIT_VARIABLE',
        },
        type: 'variable',
      },
    ]);

    modificationsStore.removeVariableModification(
      '2',
      'variable1',
      'EDIT_VARIABLE',
      'variables'
    );

    expect(modificationsStore.state.lastRemovedModification).toEqual({
      modification: {
        payload: {
          flowNodeName: 'flow-node-2',
          id: 'variable1',
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'EDIT_VARIABLE',
          scopeId: '2',
        },
        type: 'variable',
      },
      source: 'variables',
    });

    expect(modificationsStore.state.modifications.length).toEqual(0);
  });

  it('should remove last modification', async () => {
    const uniqueID = generateUniqueID();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
    });

    expect(modificationsStore.lastModification).toEqual({
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
      type: 'token',
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeId: uniqueID,
        parentScopeIds: {},
      },
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual(undefined);
  });

  it('should get modifications by flow node', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      ),

      rest.get(
        '/api/process-instances/:processInstanceId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
              {
                activityId: 'multi-instance-subprocess',
                active: 0,
                incidents: 0,
                completed: 0,
                canceled: 0,
              },
              {
                activityId: 'subprocess-service-task',
                active: 2,
                incidents: 1,
                completed: 0,
                canceled: 0,
              },
            ])
          )
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode3', name: 'flow-node-3'},
        targetFlowNode: {id: 'flowNode4', name: 'flow-node-4'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
        scopeIds: ['1', '2', '3'],
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        targetFlowNode: {id: 'flowNode6', name: 'flow-node-6'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: ['4', '5'],
        parentScopeIds: {},
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'multi-instance-subprocess', name: 'flow-node-7'},
        affectedTokenCount: 0,
        visibleAffectedTokenCount: 0,
      },
    });

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      flowNode1: {
        cancelledTokens: 0,
        newTokens: 2,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode2: {
        cancelledTokens: 3,
        newTokens: 0,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode3: {
        cancelledTokens: 3,
        newTokens: 0,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode4: {
        cancelledTokens: 0,
        newTokens: 3,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode5: {
        cancelledTokens: 2,
        newTokens: 1,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode6: {
        cancelledTokens: 0,
        newTokens: 2,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      'multi-instance-subprocess': {
        cancelledChildTokens: 3,
        cancelledTokens: 0,
        newTokens: 0,
        visibleCancelledTokens: 0,
      },
      'subprocess-end-task': {
        cancelledChildTokens: 0,
        cancelledTokens: 0,
        newTokens: 0,
        visibleCancelledTokens: 0,
      },
      'subprocess-service-task': {
        cancelledChildTokens: 0,
        cancelledTokens: 3,
        newTokens: 0,
        visibleCancelledTokens: 3,
      },
      'subprocess-start-1': {
        cancelledChildTokens: 0,
        cancelledTokens: 0,
        newTokens: 0,
        visibleCancelledTokens: 0,
      },
    });
  });

  it('should check if tokens on a flow node is cancelled', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode1')
    ).toBe(false);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      },
    });
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode1')
    ).toBe(true);

    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode(
        'non-existing-flownode'
      )
    ).toBe(false);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        targetFlowNode: {id: 'flowNode3', name: 'flow-node-3'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeIds: ['1'],
        parentScopeIds: {},
      },
    });
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode2')
    ).toBe(true);
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode3')
    ).toBe(false);
  });

  it('should move tokens', async () => {
    mockServer.use(
      rest.get(
        '/api/process-instances/:processInstanceId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
              {
                activityId: 'flowNode1',
                active: 2,
                incidents: 0,
                completed: 0,
                canceled: 0,
              },
            ])
          )
      )
    );

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);

    expect(modificationsStore.modificationsByFlowNode).toEqual({});
    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation
    ).toBeNull();

    modificationsStore.startMovingToken('flowNode1');
    expect(modificationsStore.state.sourceFlowNodeIdForMoveOperation).toBe(
      'flowNode1'
    );

    modificationsStore.finishMovingToken('flowNode2');

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      flowNode1: {
        cancelledTokens: 2,
        newTokens: 0,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      flowNode2: {
        cancelledTokens: 0,
        newTokens: 2,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
    });

    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation
    ).toBeNull();
  });

  it('should move tokens from multi instance process', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      ),
      rest.get(
        '/api/process-instances/:processId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              StartEvent_1: 'COMPLETED',
              'service-task-1': 'COMPLETED',
              'multi-instance-subprocess': 'INCIDENT',
              'subprocess-start-1': 'COMPLETED',
              'subprocess-service-task': 'INCIDENT',
              'service-task-7': 'ACTIVE',
              'multi-instance-service-task': 'ACTIVE',
              'message-boundary': 'ACTIVE',
            })
          )
      ),
      rest.get(
        '/api/process-instances/:processInstanceId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
              {
                activityId: 'multi-instance-service-task',
                active: 2,
                incidents: 0,
                completed: 0,
                canceled: 0,
              },
            ])
          )
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );
    await flowNodeStatesStore.fetchFlowNodeStates('processInstanceId');
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);
    modificationsStore.startMovingToken('multi-instance-service-task');
    modificationsStore.finishMovingToken('service-task-7');

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      'multi-instance-service-task': {
        cancelledTokens: 2,
        newTokens: 0,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
      'service-task-7': {
        cancelledTokens: 0,
        newTokens: 1,
        cancelledChildTokens: 0,
        visibleCancelledTokens: 0,
      },
    });
  });

  it('should retrieve variable modifications', () => {
    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name1',
      value: 'value1',
    });

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name1',
      value: 'value2',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name1',
        newValue: 'value2',
      },
    ]);

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name2',
      value: 'value3',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
    ]);

    createEditVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      name: 'existing-variable',
      oldValue: '12',
      newValue: '123',
    });

    createEditVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      name: 'existing-variable',
      oldValue: '12',
      newValue: '1234',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
      {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '1234',
        oldValue: '12',
      },
    ]);

    createAddVariableModification({
      scopeId: 'flow-node-2',
      flowNodeName: 'flowNode2',
      id: '1',
      name: 'name2',
      value: 'value3',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
      {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '1234',
        oldValue: '12',
      },
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-2',
        flowNodeName: 'flowNode2',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
    ]);
  });

  it('should generate parent scope ids', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockNestedSubprocess))
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );

    expect(modificationsStore.generateParentScopeIds('user_task')).toEqual({
      inner_sub_process: expect.any(String),
      parent_sub_process: expect.any(String),
    });

    expect(
      modificationsStore.generateParentScopeIds('inner_sub_process')
    ).toEqual({
      parent_sub_process: expect.any(String),
    });

    expect(
      modificationsStore.generateParentScopeIds('parent_sub_process')
    ).toEqual({});
  });

  it('should retrieve "edit variable" modifications', async () => {
    const FLOW_NODE_INSTANCE_ID = 'test-flow-node';

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '123',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test',
        'EDIT_VARIABLE'
      )
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '123',
    });

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test2',
      oldValue: '123',
      newValue: '1234',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test2',
        'EDIT_VARIABLE'
      )
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test2',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test2',
      oldValue: '123',
      newValue: '1234',
    });

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '12345',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test',
        'EDIT_VARIABLE'
      )
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '12345',
    });
  });

  it('should retrieve "add variable" modifications', async () => {
    const FLOW_NODE_INSTANCE_ID = 'test-flow-node';

    expect(modificationsStore.state.modifications).toEqual([]);
    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test',
      value: '123',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE'
      )
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test',
      newValue: '123',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID)
    ).toEqual([
      {
        id: '1',
        name: 'test',
        value: '123',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test',
      value: '1234',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE'
      )
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test',
      newValue: '1234',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID)
    ).toEqual([
      {
        id: '1',
        name: 'test',
        value: '1234',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test-updated',
      value: '1234',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE'
      )
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test-updated',
      newValue: '1234',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID)
    ).toEqual([
      {
        id: '1',
        name: 'test-updated',
        value: '1234',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '2',
      name: 'another-variable',
      value: '987',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE'
      )
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test-updated',
      newValue: '1234',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '2',
        'ADD_VARIABLE'
      )
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '2',
      name: 'another-variable',
      newValue: '987',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID)
    ).toEqual([
      {
        id: '1',
        name: 'test-updated',
        value: '1234',
      },
      {
        id: '2',
        name: 'another-variable',
        value: '987',
      },
    ]);

    expect(modificationsStore.getAddVariableModifications(null)).toEqual([]);
    expect(
      modificationsStore.getAddVariableModifications('non-existing-flow-node')
    ).toEqual([]);
  });

  it('should generate modifications payload', () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'flow_node_0', name: 'flow node 0'},
        scopeId: 'random-scope-id-0',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'flow_node_1', name: 'flow node 1'},
        scopeId: 'random-scope-id-1',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {
          'first-parent-scope': 'random-scope-id-first',
          'second-parent-scope': 'random-scope-id-second',
        },
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'flow_node_2', name: 'flow node 2'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flow_node_3', name: 'flow node 3'},
        targetFlowNode: {id: 'flow_node_4', name: 'flow node 4'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeIds: ['random-scope-id-2'],
        parentScopeIds: {
          'first-parent': 'random-scope-id-for-parent-1',
          'second-parent': 'random-scope-id-for-parent-2',
          'third-parent': 'random-scope-id-for-parent-3',
        },
      },
    });

    // add 2 variables to one of the newly created scopes
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-1',
        name: 'name1',
        newValue: '"value1"',
        id: '1',
        flowNodeName: 'flow node 1',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-1',
        name: 'name2',
        newValue: '"value2"',
        id: '2',
        flowNodeName: 'flow node 1',
      },
    });

    // add 2 variables and edit one variable to existing scopes
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name3',
        newValue: '"value3"',
        id: '3',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name4',
        newValue: '"value4"',
        id: '4',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name5',
        oldValue: '"value5"',
        newValue: '"value5-edited"',
        id: '5',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name5',
        oldValue: '"value5"',
        newValue: '"value5-edited2"',
        id: '5',
        flowNodeName: 'flow node 5',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-2',
        name: 'name6',
        newValue: '"value6"',
        id: '6',
        flowNodeName: 'flow node 4',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-1',
        name: 'name7',
        newValue: '"value7"',
        id: '7',
        flowNodeName: 'flow node 7',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-1',
        name: 'name8',
        newValue: '"value8"',
        id: '8',
        flowNodeName: 'flow node 7',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-3',
        name: 'name9',
        newValue: '"value9"',
        id: '9',
        flowNodeName: 'flow node 9',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-first',
        name: 'name10',
        newValue: '"value10"',
        id: '10',
        flowNodeName: 'flow node 10',
      },
    });

    expect(modificationsStore.generateModificationsPayload()).toEqual([
      {
        modification: 'ADD_TOKEN',
        toFlowNodeId: 'flow_node_0',
        variables: undefined,
      },
      {
        modification: 'ADD_TOKEN',
        toFlowNodeId: 'flow_node_1',
        variables: {
          'first-parent-scope': [
            {
              name10: 'value10',
            },
          ],
          flow_node_1: [{name1: 'value1', name2: 'value2'}],
        },
      },
      {modification: 'CANCEL_TOKEN', fromFlowNodeId: 'flow_node_2'},
      {
        modification: 'MOVE_TOKEN',
        fromFlowNodeId: 'flow_node_3',
        toFlowNodeId: 'flow_node_4',
        newTokensCount: 1,
        variables: {
          flow_node_4: [{name6: 'value6'}],
          'first-parent': [
            {
              name7: 'value7',
              name8: 'value8',
            },
          ],
          'third-parent': [
            {
              name9: 'value9',
            },
          ],
        },
      },
      {
        modification: 'ADD_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name3: 'value3'},
      },
      {
        modification: 'ADD_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name4: 'value4'},
      },
      {
        modification: 'EDIT_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name5: 'value5-edited2'},
      },
    ]);
  });
});
