// Okio/kotlinx-io retain optional Node helpers in their JS artifacts. Browser tests
// use their browser-safe paths; Node's os/path modules are intentionally unavailable.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
  os: false,
  path: false,
});
