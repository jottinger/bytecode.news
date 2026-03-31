import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearAuth, getAuthState, setAuth, validateAuthSession } from "@/lib/client-auth";

type WindowMock = {
  localStorage: Storage;
  addEventListener: (type: string, handler: EventListener) => void;
  removeEventListener: (type: string, handler: EventListener) => void;
  dispatchEvent: (event: Event) => boolean;
};

function createWindowMock(): WindowMock {
  const listeners = new Map<string, Set<EventListener>>();
  const store = new Map<string, string>();
  const localStorage: Storage = {
    get length() {
      return store.size;
    },
    clear() {
      store.clear();
    },
    getItem(key: string) {
      return store.get(key) ?? null;
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null;
    },
    removeItem(key: string) {
      store.delete(key);
    },
    setItem(key: string, value: string) {
      store.set(key, value);
    },
  };

  return {
    localStorage,
    addEventListener(type: string, handler: EventListener) {
      const set = listeners.get(type) ?? new Set<EventListener>();
      set.add(handler);
      listeners.set(type, set);
    },
    removeEventListener(type: string, handler: EventListener) {
      listeners.get(type)?.delete(handler);
    },
    dispatchEvent(event: Event) {
      listeners.get(event.type)?.forEach((handler) => handler(event));
      return true;
    },
  };
}

describe("client auth", () => {
  const originalWindow = globalThis.window;

  beforeEach(() => {
    Object.defineProperty(globalThis, "window", {
      configurable: true,
      value: createWindowMock(),
    });
  });

  afterEach(() => {
    if (originalWindow) {
      Object.defineProperty(globalThis, "window", {
        configurable: true,
        value: originalWindow,
      });
    } else {
      Reflect.deleteProperty(globalThis, "window");
    }
  });

  it("returns no-token when there is no stored principal", async () => {
    const fetcher = vi.fn();
    const result = await validateAuthSession(fetcher as unknown as typeof fetch);
    expect(result).toBe("no-token");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("returns valid when backend accepts session cookie", async () => {
    setAuth({
      token: "abc",
      principal: { id: "1", username: "dreamreal", displayName: "dreamreal", role: "USER" },
    });

    const fetcher = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    const result = await validateAuthSession(fetcher as unknown as typeof fetch);
    expect(result).toBe("valid");
    expect(getAuthState().principal).not.toBeNull();
  });

  it("attempts silent refresh on 401 before clearing auth", async () => {
    setAuth({
      token: "abc",
      principal: { id: "1", username: "dreamreal", displayName: "dreamreal", role: "USER" },
    });

    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const result = await validateAuthSession(fetcher as unknown as typeof fetch);
    expect(result).toBe("expired");
    expect(getAuthState().principal).toBeNull();
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("returns refreshed when silent refresh succeeds after 401", async () => {
    setAuth({
      token: "abc",
      principal: { id: "1", username: "dreamreal", displayName: "dreamreal", role: "USER" },
    });

    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            token: "new-token",
            principal: { id: "1", username: "dreamreal", displayName: "dreamreal", role: "USER" },
          }),
          { status: 200 },
        ),
      );
    const result = await validateAuthSession(fetcher as unknown as typeof fetch);
    expect(result).toBe("refreshed");
    expect(getAuthState().principal).not.toBeNull();
  });

  it("reports network-error on fetch failure without clearing auth", async () => {
    setAuth({
      token: "abc",
      principal: { id: "1", username: "dreamreal", displayName: "dreamreal", role: "USER" },
    });

    const fetcher = vi.fn().mockRejectedValue(new Error("offline"));
    const result = await validateAuthSession(fetcher as unknown as typeof fetch);
    expect(result).toBe("network-error");
    expect(getAuthState().principal).not.toBeNull();
    clearAuth();
  });
});
