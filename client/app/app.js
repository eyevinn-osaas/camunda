import {addSplash} from './splash';

const removeSplash = addSplash();

require.ensure(['lodash.isequal', 'redux', './init'], () => {
  if (isPolyfillNeeded()) {
    require.ensure(['babel-polyfill', 'whatwg-fetch'], () => {
      removeSplash();

      require('babel-polyfill');
      require('whatwg-fetch');

      require('./init');
    });
  } else {
    removeSplash();

    require('./init');
  }
});

require.ensure(['./styles.scss'], () => {
  require('./styles.scss');
});

function isPolyfillNeeded() {
  return !Symbol || !Array.prototype.find;
}
