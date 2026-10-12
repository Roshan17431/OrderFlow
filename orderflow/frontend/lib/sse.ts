import { API_BASE, refreshSession } from "@/lib/api/http";
import { session } from "@/lib/auth/session";
export interface SseEvent {
  id?: string;
  event: string;
  data: string;
}
export class SseParser {
  private buffer = "";
  feed(chunk: string): SseEvent[] {
    this.buffer = (this.buffer + chunk).replaceAll("\r\n", "\n");
    if (this.buffer.length > 65536) throw new Error("SSE event too large");
    const blocks = this.buffer.split("\n\n");
    this.buffer = blocks.pop() ?? "";
    const events: SseEvent[] = [];
    for (const block of blocks) {
      let event = "message",
        id: string | undefined;
      const data: string[] = [];
      for (const line of block.split("\n")) {
        if (line.startsWith(":")) continue;
        const colon = line.indexOf(":");
        const field = colon < 0 ? line : line.slice(0, colon);
        const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
        if (field === "data") data.push(value);
        if (field === "event") event = value;
        if (field === "id" && !value.includes("\0")) id = value;
      }
      if (data.length)
        events.push({ ...(id ? { id } : {}), event, data: data.join("\n") });
    }
    return events;
  }
}
function wait(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const done = () => {
      signal.removeEventListener("abort", abort);
      resolve();
    };
    const timer = setTimeout(done, ms);
    const abort = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", abort);
      reject(signal.reason);
    };
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) abort();
  });
}
export async function connectNotifications(
  signal: AbortSignal,
  onEvent: (event: SseEvent) => void,
  onState: (state: "live" | "reconnecting" | "polling") => void,
) {
  const seen = new Set<string>();
  let attempts = 0;
  while (!signal.aborted && attempts < 6) {
    try {
      const token = session.get().token;
      if (!token) return;
      const response = await fetch(API_BASE + "/api/notifications/stream", {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "text/event-stream",
          "X-Correlation-ID": crypto.randomUUID(),
        },
        credentials: "include",
        cache: "no-store",
        signal,
      });
      if (response.status === 401) {
        await refreshSession();
        throw new Error("Reconnect after refresh");
      }
      if (!response.ok || !response.body) throw new Error("Stream unavailable");
      onState("live");
      const parser = new SseParser();
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      try {
        while (!signal.aborted) {
          const { value, done } = await reader.read();
          if (done) break;
          for (const e of parser.feed(
            decoder.decode(value, { stream: true }),
          )) {
            if (e.event !== "notification") continue;
            if (e.id && seen.has(e.id)) continue;
            if (e.id) {
              seen.add(e.id);
              if (seen.size > 100) seen.delete(seen.values().next().value!);
            }
            onEvent(e);
          }
        }
      } finally {
        await reader.cancel().catch(() => {});
        reader.releaseLock();
      }
    } catch {
      if (signal.aborted) return;
    }
    attempts++;
    if (attempts < 6) {
      onState("reconnecting");
      await wait(Math.min(1000 * 2 ** (attempts - 1), 30000), signal).catch(
        () => {},
      );
    }
  }
  if (!signal.aborted) onState("polling");
}
