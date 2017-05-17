import {expect} from 'chai';
import {routeReducer,
  createCreateVariableFilterAction,
} from 'main/processDisplay/controls/filter/variable/routeReducer';

describe('variable route reducer', () => {
  it('initially create an empty array', () => {
    const action = {type: 'INIT'};
    const state = routeReducer(undefined, action);

    expect(state).to.be.an('array');
    expect(state).to.have.a.lengthOf(0);
  });

  it('should append new filter entries', () => {
    const newFilter = {name: 'a', type: 'b', operator: 'c', values: ['x']};
    const action = createCreateVariableFilterAction(newFilter);
    const state = routeReducer([1, 2, 3], action);

    expect(state).to.have.a.lengthOf(4);
    expect(state[state.length - 1].type).to.eql('variable');
    expect(state[state.length - 1].data).to.be.an('array');
    expect(state[state.length - 1].data).to.contain(newFilter.name);
    expect(state[state.length - 1].data).to.contain(newFilter.type);
    expect(state[state.length - 1].data).to.contain(newFilter.operator);
    expect(state[state.length - 1].data).to.contain(newFilter.values);
  });
});
