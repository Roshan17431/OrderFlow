import { it, expect } from "vitest";
import { SseParser } from "@/lib/sse";
it("parses chunk boundaries multiline CRLF and ignores keepalive comments", () => {
  const p = new SseParser();
  expect(
    p.feed(':keepalive\r\n\r\nid: 1\r\nevent: notification\r\ndata: {"a":\r'),
  ).toEqual([]);
  expect(p.feed("\ndata: 1}\r\n\r\n")).toEqual([
    { id: "1", event: "notification", data: '{"a":\n1}' },
  ]);
});
it("rejects oversized unfinished events", () =>
  expect(() => new SseParser().feed("x".repeat(65537))).toThrow(/large/));
