import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {FilterBar, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/FilterBar';

describe('<FilterBar>', () => {
  let node;
  let update;
  let DateFilter;

  beforeEach(() => {
    DateFilter = createMockComponent('DateFilter');
    __set__('DateFilter', DateFilter);

    ({node, update} = mountTemplate(<FilterBar />));
  });

  afterEach(() => {
    __ResetDependency__('DateFilter');
  });

  it('should contain a filter list', () => {
    expect(node.querySelector('ul')).to.exist;
  });

  it('should be empty by default', () => {
    update({filter: []});

    expect(node.querySelector('ul').textContent).to.be.empty;
  });

  it('should contain a representation of the filter', () => {
    update({filter: [
      {type: 'startDate'}
    ]});

    expect(node.querySelector('ul').textContent).to.eql('DateFilter');
  });
});
