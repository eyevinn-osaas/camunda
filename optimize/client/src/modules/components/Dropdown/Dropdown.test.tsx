/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CommonWrapper, mount, shallow} from 'enzyme';
import {ReactNode, act} from 'react';

import {getScreenBounds} from 'services';

import Dropdown from './Dropdown';
import {findLetterOption} from './service';

jest.mock('components', () => {
  return {
    Button: ({active, ...props}: {active?: boolean}) => <button {...props} />,
    Icon: () => <span />,
    Select: () => ({Submenu: <div />}),
  };
});

jest.mock('./DropdownOption', () => {
  return (props: {children: ReactNode}) => {
    return <button className="DropdownOption">{props.children}</button>;
  };
});

jest.mock('./Submenu', () => (props: object) => (
  <div tabIndex={0} className="Submenu">
    Submenu: {JSON.stringify(props)}
  </div>
));

jest.mock('./service', () => ({findLetterOption: jest.fn()}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  getScreenBounds: jest.fn().mockReturnValue({top: 0, bottom: 100}),
}));

Object.defineProperty(window.HTMLElement.prototype, 'offsetParent', {
  value: {
    getBoundingClientRect: () => ({
      top: 0,
      left: 0,
    }),
  },
});

let container: HTMLDivElement;

beforeEach(() => {
  container = document.createElement('div');
  document.body.append(container);
});

afterEach(() => {
  document.body.removeChild(container);
});

function simulateDropdown(
  node: CommonWrapper<Dropdown['props'], Dropdown['state'], Dropdown>,
  {
    oneItemHeight,
    buttonPosition,
    menuHeight,
    menuPosition,
    screenBottom,
    screenTop,
  }: {
    oneItemHeight: number;
    buttonPosition: Partial<DOMRect>;
    menuHeight: number;
    menuPosition: Partial<DOMRect>;
    screenBottom: number;
    screenTop: number;
  }
) {
  node.instance().container = {
    querySelector: () => ({
      offsetHeight: 10,
      getBoundingClientRect: () => buttonPosition,
      offsetParent: {
        getBoundingClientRect: () => ({
          top: 0,
          left: 0,
        }),
      },
    }),
  } as unknown as HTMLElement;

  node.instance().menuContainer = {
    current: {
      clientHeight: menuHeight,
      querySelector: () => ({clientHeight: oneItemHeight}),
      getBoundingClientRect: () => menuPosition,
    } as unknown as HTMLDivElement,
  };

  const footer = document.createElement('div');
  (getScreenBounds as jest.Mock).mockReturnValueOnce({top: screenTop, bottom: screenBottom});
  document.body.appendChild(footer);

  const header = document.createElement('div');
  document.body.appendChild(header);
}

it('should render without crashing', () => {
  mount(<Dropdown label="label" />);
});

it('should contain the specified label', () => {
  const node = mount(<Dropdown label="Click me" />);

  expect(node.find('span').at(0)).toIncludeText('Click me');
});

it('should display the child elements when clicking the trigger', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.find('button.activateButton').simulate('click');
  });

  node.update();

  expect(node.find('.Dropdown')).toMatchSelector('.is-open');
});

it('should call onOpen if provided', () => {
  const spy = jest.fn();
  const node = mount(
    <Dropdown label="Click me" onOpen={spy}>
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.find('button.activateButton').simulate('click');
  });

  expect(spy).toHaveBeenCalledWith(true);
});

it('should close when clicking somewhere', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.setState({open: true});

    node.simulate('click');

    expect(node.state('open')).toBe(false);
    expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
  });
});

it('should close when selecting an option', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>
        <p className="test_option">foo</p>
      </Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.setState({open: true});
  });

  node.find('.test_option').simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should set aria-haspopup to true', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('button.activateButton')).toMatchSelector(
    '.activateButton[aria-haspopup="true"]'
  );
});

it('should set aria-expanded to false by default', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.activateButton[aria-expanded="false"]')).toExist();
});

it('should set aria-expanded to true when open', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.simulate('click');
  });

  node.update();

  expect(node.state('open')).toBe(true);
  expect(node.find('.activateButton[aria-expanded="true"]')).toExist();
});

it('should set aria-expanded to false when closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.setState({open: true});
    node.simulate('click');

    node.update();

    expect(node.state('open')).toBe(false);
    expect(node.find('.activateButton[aria-expanded="false"]')).toExist();
  });
});

it('should set aria-labelledby on the menu as provided as a prop, amended by "-button"', () => {
  const node = mount(
    <Dropdown label="label" id="my-dropdown">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.menu[aria-labelledby="my-dropdown-button"]')).toExist();
});

it('should close after pressing Esc', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    node.setState({open: true});

    node.simulate('keyDown', {key: 'Escape', keyCode: 27, which: 27});

    expect(node.state('open')).toBe(false);
  });
});

it('should not change focus after pressing an arrow key if closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>,
    {attachTo: container}
  );

  act(() => {
    node.find('button').first().simulate('click');

    node.simulate('keyDown', {key: 'ArrowDown'});
  });

  node.update();

  expect(document.activeElement?.textContent).toBe('Click me');
});

it('should change focus after pressing an arrow key if opened', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>,
    {attachTo: container}
  );

  act(() => {
    node.find('button').first().simulate('click');

    node.instance().setState({open: true});

    node.simulate('keyDown', {
      key: 'ArrowDown',
    });

    expect(document.activeElement?.textContent).toBe('foo');
    node.simulate('keyDown', {
      key: 'ArrowDown',
    });
    expect(document.activeElement?.textContent).toBe('bar');
  });
});

it('should pass open, offset, setOpened, setClosed, forceToggle and closeParent properties to submenus', () => {
  const node = mount(
    <Dropdown label="label">
      <Dropdown.Submenu label="label" />
    </Dropdown>
  );

  const submenuNode = node.find(Dropdown.Submenu);
  expect(submenuNode).toHaveProp('open');
  expect(submenuNode).toHaveProp('offset');
  expect(submenuNode).toHaveProp('setOpened');
  expect(submenuNode).toHaveProp('setClosed');
  expect(submenuNode).toHaveProp('forceToggle');
  expect(submenuNode).toHaveProp('closeParent');
});

it('should add scrollable class when there is no enough space to show all items', () => {
  const node = shallow<Dropdown>(<Dropdown label="label" />);

  const specs = {
    oneItemHeight: 30,
    buttonPosition: {bottom: 0, top: 0, left: 0, height: 10, width: 100},
    menuHeight: 160,
    menuPosition: {top: 0},
    screenBottom: 150,
    screenTop: 0,
  };

  simulateDropdown(node, specs);

  node.instance().calculateMenuStyle(true);
  node.update();

  expect(node.state().listStyles.height).toBe(specs.screenBottom - 10);
  expect(node.find('.menu > DropdownOptionsList').first()).toHaveClassName('scrollable');
});

it('flip dropdown vertically when there is no enough space', () => {
  const node = shallow<Dropdown>(
    <Dropdown label="label">
      <Dropdown.Option>1</Dropdown.Option>
      <Dropdown.Option>2</Dropdown.Option>
      <Dropdown.Option>3</Dropdown.Option>
      <Dropdown.Option>4</Dropdown.Option>
      <Dropdown.Option>5</Dropdown.Option>
    </Dropdown>
  );

  const specs = {
    oneItemHeight: 30,
    buttonPosition: {bottom: 50, top: 200, left: 0, height: 10, width: 100},
    menuHeight: 70,
    menuPosition: {top: 53},
    screenBottom: 110,
    screenTop: 0,
  };

  simulateDropdown(node, specs);

  node.instance().calculateMenuStyle(true);
  node.update();

  expect(node.state().menuStyle.top).toBe(specs.buttonPosition.top - specs.menuHeight - 6);
});

it('should not add scrollable class when the item is flipped and there is enough space above the item', () => {
  const node = mount<Dropdown>(
    <Dropdown label="label">
      <Dropdown.Option>1</Dropdown.Option>
      <Dropdown.Option>2</Dropdown.Option>
      <Dropdown.Option>3</Dropdown.Option>
      <Dropdown.Option>4</Dropdown.Option>
      <Dropdown.Option>5</Dropdown.Option>
    </Dropdown>
  );

  act(() => {
    simulateDropdown(node, {
      oneItemHeight: 30,
      buttonPosition: {top: 500, bottom: 535, left: 0, height: 10, width: 100},
      menuHeight: 400,
      menuPosition: {top: 503},
      screenBottom: 550,
      screenTop: 10,
    });

    node.instance().calculateMenuStyle(true);
    node.update();

    expect(node.find('.menu > ul').first()).not.toHaveClassName('scrollable');
  });
});

it('should invoke findLetterOption when typing a character', () => {
  const node = mount(
    <Dropdown label="label">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>far</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>,
    {attachTo: container}
  );

  node.find(Dropdown.Option).last().getDOMNode<HTMLElement>().focus();

  node.simulate('keyDown', {key: 'f', keyCode: 70});
  expect((findLetterOption as jest.Mock).mock.calls[0][1]).toBe('f');
  expect((findLetterOption as jest.Mock).mock.calls[0][2]).toBe(3);
});
