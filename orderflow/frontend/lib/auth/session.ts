import type { Authentication, User } from "@/types/contracts";
export interface Session {
  user: User | null;
  token: string | null;
  status: "loading" | "anonymous" | "authenticated";
  epoch: number;
}
let state: Session = { user: null, token: null, status: "loading", epoch: 0 };
const listeners = new Set<() => void>();
export const session = {
  get: () => state,
  subscribe: (fn: () => void) => {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
  set: (value: Authentication, epoch = state.epoch) => {
    if (epoch !== state.epoch) return false;
    const switched = state.user != null && state.user.id !== value.user.id;
    state = {
      ...state,
      user: value.user,
      token: value.accessToken,
      status: "authenticated",
      epoch: state.epoch + (switched ? 1 : 0),
    };
    listeners.forEach((fn) => fn());
    return true;
  },
  clear: () => {
    state = {
      user: null,
      token: null,
      status: "anonymous",
      epoch: state.epoch + 1,
    };
    listeners.forEach((fn) => fn());
  },
};
