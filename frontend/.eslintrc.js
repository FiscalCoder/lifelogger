module.exports = {
  root: true,
  extends: '@react-native',
  rules: {
    'no-console': ['warn', {allow: ['warn', 'error']}],
    '@typescript-eslint/no-explicit-any': 'warn',
    'react-native/no-inline-styles': 'warn',
  },
};
