import { beforeEach, describe, expect, it, vi } from "vitest";

function deferred() {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

async function loadMain({ initAuthPromise, getFeaturesPromise, cachedFeatures }) {
  vi.resetModules();

  let featuresListener = () => {};
  let featuresState = cachedFeatures;

  const initAuth = vi.fn(() => initAuthPromise);
  const getFeatures = vi.fn(() => getFeaturesPromise);
  const getCachedFeatures = vi.fn(() => featuresState);
  const onFeaturesChange = vi.fn((fn) => {
    featuresListener = fn;
  });
  const renderNav = vi.fn();
  const startRouter = vi.fn();
  const refreshRoutes = vi.fn();
  const resetTitle = vi.fn();

  vi.doMock("../src/auth.js", () => ({ initAuth }));
  vi.doMock("../src/features.js", () => ({ getFeatures, getCachedFeatures, onFeaturesChange }));
  vi.doMock("../src/components/nav.js", () => ({ renderNav }));
  vi.doMock("../src/router.js", () => ({ startRouter, refreshRoutes }));
  vi.doMock("../src/title.js", () => ({ resetTitle }));

  await import("../src/main.js");

  return {
    initAuth,
    getFeatures,
    renderNav,
    startRouter,
    refreshRoutes,
    resetTitle,
    setFeatures(next) {
      featuresState = next;
    },
    triggerFeatureUpdate() {
      featuresListener();
    },
  };
}

describe("main boot", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <header><a href="/">Nevet</a></header>
      <link type="application/rss+xml" title="Nevet RSS">
      <main id="app"></main>
    `;
  });

  it("renders nav and starts router before hydration finishes", async () => {
    const initAuthDeferred = deferred();
    const getFeaturesDeferred = deferred();

    const ctx = await loadMain({
      initAuthPromise: initAuthDeferred.promise,
      getFeaturesPromise: getFeaturesDeferred.promise,
      cachedFeatures: null,
    });

    expect(ctx.renderNav).toHaveBeenCalledTimes(1);
    expect(ctx.startRouter).toHaveBeenCalledTimes(1);
    expect(ctx.getFeatures).not.toHaveBeenCalled();

    initAuthDeferred.resolve();
    await Promise.resolve();
    expect(ctx.getFeatures).toHaveBeenCalledTimes(1);

    getFeaturesDeferred.resolve(null);
  });

  it("refreshes nav and routes when features are hydrated", async () => {
    const ctx = await loadMain({
      initAuthPromise: Promise.resolve(),
      getFeaturesPromise: Promise.resolve(null),
      cachedFeatures: null,
    });

    ctx.setFeatures({ siteName: "Bytecode", operationGroups: ["FACTOID"] });
    ctx.triggerFeatureUpdate();

    expect(ctx.renderNav).toHaveBeenCalledTimes(2);
    expect(ctx.refreshRoutes).toHaveBeenCalledTimes(1);
    expect(ctx.resetTitle).toHaveBeenCalledTimes(1);
    expect(document.querySelector("header a[href='/']").textContent).toBe("Bytecode");
    expect(document.querySelector("link[type='application/rss+xml']").title).toBe("Bytecode RSS");
  });
});
