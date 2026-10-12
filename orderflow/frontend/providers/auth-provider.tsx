"use client";
import {
  createContext,
  useContext,
  useEffect,
  useSyncExternalStore,
  useCallback,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { refreshSession } from "@/lib/api/http";
import { session, type Session } from "@/lib/auth/session";
import type { Authentication } from "@/types/contracts";
const initial: Session = {
  user: null,
  token: null,
  status: "loading",
  epoch: 0,
};
type Context = Session & {
  login: (body: { email: string; password: string }) => Promise<Authentication>;
  register: (body: {
    email: string;
    password: string;
    displayName: string;
  }) => Promise<Authentication>;
  logout: (all?: boolean) => Promise<void>;
};
const AuthContext = createContext<Context | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const value = useSyncExternalStore(
    session.subscribe,
    session.get,
    () => initial,
  );
  const cache = useQueryClient();
  const clearPrivate = useCallback(() => {
    const privateQuery = (query: { queryKey: readonly unknown[] }) =>
      !(
        query.queryKey[0] === "catalog" ||
        query.queryKey[0] === "product" ||
        (query.queryKey[0] === "categories" &&
          query.queryKey[1] === "public") ||
        (query.queryKey[0] === "products" && query.queryKey[1] === "categories")
      );
    void cache.cancelQueries({ predicate: privateQuery });
    cache.removeQueries({ predicate: privateQuery });
    cache.getMutationCache().clear();
  }, [cache]);

  useEffect(() => {
    let epoch = session.get().epoch;
    const unsubscribe = session.subscribe(() => {
      const next = session.get();
      if (next.status === "anonymous" || next.epoch !== epoch) clearPrivate();
      epoch = next.epoch;
    });
    if (session.get().status === "loading")
      void refreshSession().catch(() => {});
    return unsubscribe;
  }, [clearPrivate]);
  async function accept(action: () => Promise<Authentication>) {
    clearPrivate();
    session.clear();
    const epoch = session.get().epoch;
    const result = await action();
    session.set(result, epoch);
    return result;
  }
  async function logout(all = false) {
    const epoch = session.get().epoch;
    try {
      await api.auth.logout(all);
    } finally {
      if (session.get().epoch === epoch) {
        session.clear();
        clearPrivate();
      }
    }
  }
  return (
    <AuthContext.Provider
      value={{
        ...value,
        login: (body) => accept(() => api.auth.login(body)),
        register: (body) => accept(() => api.auth.register(body)),
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider missing");
  return value;
}
