import React from 'react';
import {mount} from 'enzyme';
import {HashRouter as Router, Redirect} from 'react-router-dom';

import Dropdown from 'modules/components/Dropdown';
import Header from './Header';
import Badge from 'modules/components/Badge';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

import * as api from 'modules/api/header/header';
import * as instancesApi from 'modules/api/instances/instances';

import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';
import {DEFAULT_FILTER} from 'modules/constants';

import {apiKeys, filtersMap} from './constants';
import * as Styled from './styled.js';

const USER = {
  user: {
    firstname: 'Jonny',
    lastname: 'Prosciutto'
  }
};
const INSTANCES_COUNT = 23;

// component mocks
// avoid loop of redirects when testing handleLogout
jest.mock(
  'react-router-dom/Redirect',
  () =>
    function Redirect(props) {
      return <div />;
    }
);

// props mocks
const mockCollapsablePanelProps = {
  getStateLocally: jest.fn(),
  isFiltersCollapsed: false,
  isSelectionsCollapsed: false,
  expandFilters: jest.fn(),
  expandSelections: jest.fn()
};

// api mocks
api.fetchUser = mockResolvedAsyncFn(USER);
api.logout = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesCount = mockResolvedAsyncFn(INSTANCES_COUNT);

describe('Header', () => {
  const mockValues = {
    filter: {foo: 'bar'},
    filterCount: 1,
    selectionCount: 2,
    instancesInSelectionsCount: 3
  };
  beforeEach(() => {
    api.fetchUser.mockClear();
    instancesApi.fetchWorkflowInstancesCount.mockClear();
  });

  describe('localState values', () => {
    it('should render the right links', async () => {
      const mockProps = {
        ...mockValues,
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({})
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      expect(node.find(Styled.Menu)).toExist();
      expect(node.find(Styled.Menu).props().role).toBe('navigation');

      expect(node.find(Styled.Menu).find('li').length).toBe(5);

      const DashboardLinkNode = node.find(
        '[data-test="header-link-dashboard"]'
      );
      expect(DashboardLinkNode).toExist();
      expect(DashboardLinkNode.text()).toContain('Dashboard');
      expect(DashboardLinkNode.find(Styled.LogoIcon)).toExist();

      const InstancesLinkNode = node.find(
        '[data-test="header-link-instances"]'
      );
      expect(InstancesLinkNode).toExist();
      expect(InstancesLinkNode.text()).toContain('Running Instances');
      expect(InstancesLinkNode.find(Badge).text()).toBe(
        INSTANCES_COUNT.toString()
      );
      expect(InstancesLinkNode.find(Badge).props().type).toBe(
        'RUNNING_INSTANCES'
      );

      const FiltersLinkNode = node.find('[data-test="header-link-filters"]');
      expect(FiltersLinkNode).toExist();
      expect(FiltersLinkNode.text()).toContain('Filters');
      expect(FiltersLinkNode.find(Badge).text()).toBe(
        mockValues.filterCount.toString()
      );
      expect(FiltersLinkNode.find(Badge).props().type).toBe('FILTERS');

      const IncidentsLinkNode = node.find(
        '[data-test="header-link-incidents"]'
      );
      expect(IncidentsLinkNode).toExist();
      expect(IncidentsLinkNode.text()).toContain('Incidents');
      expect(IncidentsLinkNode.find(Badge).text()).toBe(
        INSTANCES_COUNT.toString().toString()
      );
      expect(IncidentsLinkNode.find(Badge).props().type).toBe('INCIDENTS');

      const SelectionsLinkNode = node.find(
        '[data-test="header-link-selections"]'
      );
      expect(SelectionsLinkNode).toExist();
      expect(SelectionsLinkNode.text()).toContain('Selections');
      expect(SelectionsLinkNode.find(Badge).length).toBe(2);
      expect(
        SelectionsLinkNode.find(Badge)
          .at(0)
          .text()
      ).toBe(mockValues.selectionCount.toString());
      expect(
        SelectionsLinkNode.find(Badge)
          .at(1)
          .text()
      ).toBe(mockValues.instancesInSelectionsCount.toString());
    });
    it("should get the filterCount, selectionCount & instancesInSelectionsCount from props if it's provided", () => {
      const mockProps = {
        ...mockValues,
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({})
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // then
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find('Badge')
          .text()
      ).toEqual(mockValues.filterCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(0)
          .text()
      ).toEqual(mockValues.selectionCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(1)
          .text()
      ).toEqual(mockValues.instancesInSelectionsCount.toString());
    });

    it('it should add default filter if no filter read from localStorage', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => {
          return {selectionCount: 2, instancesInSelectionsCount: 3};
        }
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();

      // then
      const encodedFilter = encodeURIComponent(
        '{"active":true,"incidents":true}'
      );
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .childAt(0)
          .props().to
      ).toEqual(`/instances?filter=${encodedFilter}`);
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find(Badge)
          .text()
      ).toEqual(INSTANCES_COUNT.toString());
    });

    it("should get filterCount, selectionCount & instancesInSelectionsCount from localState if it's not provided by the props", async () => {
      // given
      const propsMock = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => mockValues
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...propsMock} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();

      // then
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find('Badge')
          .text()
      ).toEqual(mockValues.filterCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(0)
          .text()
      ).toEqual(mockValues.selectionCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(1)
          .text()
      ).toEqual(mockValues.instancesInSelectionsCount.toString());
    });
  });

  describe('api values', () => {
    it("should get the value from props if it's provided", async () => {
      const mockApiProps = {
        runningInstancesCount: 1,
        incidentsCount: 2
      };
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockApiProps,
        ...mockValues
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();
      // then
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find('Badge')
          .text()
      ).toEqual(mockApiProps.runningInstancesCount.toString());
      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find('Badge')
          .text()
      ).toEqual(mockApiProps.incidentsCount.toString());
    });

    it("should get the value from api if it's not in the props", async () => {
      // given
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      apiKeys.forEach(async key => {
        // then
        expect(instancesApi.fetchWorkflowInstancesCount).toBeCalledWith(
          filtersMap[key]
        );
      });
      // then
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find('Badge')
          .text()
      ).toEqual(INSTANCES_COUNT.toString());
      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find('Badge')
          .text()
      ).toEqual(INSTANCES_COUNT.toString());
    });
  });

  describe('links highlights', () => {
    it('should highlight the dashboard link when active="dashboard"', () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'dashboard',
        getStateLocally: () => ({})
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );
      let dashboardNode = node.find('[data-test="header-link-dashboard"]');

      // then
      expect(dashboardNode.childAt(0).prop('isActive')).toBe(true);
    });

    it('should not highlight the dashboard link when active!="dashboard"', () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'instances',
        getStateLocally: () => ({})
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );
      let dashboardNode = node.find('[data-test="header-link-dashboard"]');

      // then
      expect(dashboardNode.childAt(0).prop('isActive')).toBe(false);
    });

    it('should highlight filters link when filters is not collapsed', () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'instances',
        isFiltersCollapsed: false,
        getStateLocally: () => ({})
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );
      let filtersNode = node.find('[data-test="header-link-filters"]');

      // then
      expect(filtersNode.childAt(0).prop('isActive')).toBe(true);
    });

    it('should not highlight filters link when filters is collapsed', async () => {
      // given
      // we mount Header.WrappedComponent as we need to overwrite the value of
      // isFiltersCollapsed from CollapsablePanelProvider
      const mockProps = {
        ...mockCollapsablePanelProps,
        ...mockValues,
        active: 'instances',
        isFiltersCollapsed: true
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header.WrappedComponent {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      let filtersNode = node.find('[data-test="header-link-filters"]');

      // then
      expect(filtersNode.childAt(0).prop('isActive')).toBe(false);
    });

    it('should highlight selections link when selections is not collapsed', () => {
      // (1) when selections is not collapsed
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        isSelectionsCollapsed: false,
        active: 'instances',
        getStateLocally: () => mockValues
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header.WrappedComponent {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );
      let selectionsNode = node.find('[data-test="header-link-selections"]');

      // then
      expect(selectionsNode.childAt(0).prop('isActive')).toBe(true);
    });

    it('should not highlight selections link when selections is collapsed', () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        isSelectionsCollapsed: true,
        active: 'instances',
        getStateLocally: () => mockValues
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );
      let selectionsNode = node.find('[data-test="header-link-selections"]');

      // then
      expect(selectionsNode.childAt(0).prop('isActive')).toBe(false);
    });

    it('should highlight running instance link when the filter equals the DEFAULT_FILTER', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        active: 'instances',
        filter: DEFAULT_FILTER
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      let instancesNode = node.find('[data-test="header-link-instances"]');

      // then
      expect(instancesNode.childAt(0).prop('isActive')).toBe(true);
    });

    it('should not highlight running instance link when the filter !== DEFAULT_FILTER', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        active: 'instances',
        filter: {incidents: true}
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      let instancesNode = node.find('[data-test="header-link-instances"]');

      // then
      expect(instancesNode.childAt(0).prop('isActive')).toBe(false);
    });

    it('should highlight incidents link when the filter equals incidents', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        ...mockValues,
        filter: {incidents: true},
        active: 'instances'
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header.WrappedComponent {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      let incidentsNode = node.find('[data-test="header-link-incidents"]');

      // then
      expect(incidentsNode.childAt(0).prop('isActive')).toBe(true);
    });

    it('should highlight incidents link when the filter not equals incidents', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        filter: DEFAULT_FILTER
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      let incidentsNode = node.find('[data-test="header-link-incidents"]');

      // then
      expect(incidentsNode.childAt(0).prop('isActive')).toBe(false);
    });
  });

  describe('detail', () => {
    it('should render the provided detail', () => {
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header
                {...mockProps}
                detail={<div data-test="header-detail">Detail</div>}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      expect(node.find('[data-test="header-detail"]')).toExist();
    });
  });

  describe('Userarea', () => {
    it('it should request user information', async () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        filter: DEFAULT_FILTER
      };

      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();

      expect(api.fetchUser).toHaveBeenCalled();
    });

    it('it should display user firstname and lastname', async () => {
      const mockProps = {
        getStateLocally: () => ({})
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // await user data fetching
      await flushPromises();
      node.update();

      // check user firstname and lastname are shown in the Header
      const DropdownLabel = node.find('Dropdown').prop('label');
      expect(DropdownLabel).toContain(USER.firstname);
      expect(DropdownLabel).toContain(USER.lastname);
    });

    // id fails, can't access Dropdown.Option, is inside option
    it('should logout the user when calling handleLogout', async () => {
      api.logout = mockResolvedAsyncFn();
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({})
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // await user data fetching
      await flushPromises();
      node.update();
      node.find(Dropdown).simulate('click');

      node.update();
    });

    it('assign handleLogout as a Dropdown.Option onClick', async () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({})
      };
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <Header {...mockProps} router={{}} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      //when
      node.find('button[data-test="dropdown-toggle"]').simulate('click');
      node.update();
      const onClick = node.find(Dropdown.Option).prop('onClick');

      await onClick();
      node.update();

      expect(api.logout).toHaveBeenCalled();
    });
  });
});
