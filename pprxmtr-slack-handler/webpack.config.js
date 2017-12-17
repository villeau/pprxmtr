const path = require('path');
const slsw = require('serverless-webpack');
const JSONBundlerPlugin = require('webpack-json-bundler-plugin');

module.exports = {
  entry: slsw.lib.entries,
  target: 'node',
  module: {
    rules: [{
      test: /\.js$/,
      loader: 'babel-loader',
      include: __dirname,
      exclude: /node_modules/,
      options: {
        presets: [
          ['env', {
            'targets': {
              'node': '6.10'
            }
          }],
          'stage-0'
        ]
      }
    }],
  },
  output: {
    libraryTarget: 'commonjs',
    path: path.join(__dirname, '.webpack'),
    filename: '[name].js',
  },
  plugins: [
    new JSONBundlerPlugin({
      fileInput: '*.json',
          rootDirectory: 'locales',
      localeDirectory: 'locales/'
    }),
  ]
};
