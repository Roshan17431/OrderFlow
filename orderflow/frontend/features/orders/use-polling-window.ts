"use client";
import { useEffect, useState } from "react";
export function usePollingWindow() {
  const [startedAt, setStartedAt] = useState(() => Date.now());
  const [expired, setExpired] = useState(false);
  useEffect(() => {
    const timer = setTimeout(() => setExpired(true), 120000);
    return () => clearTimeout(timer);
  }, [startedAt]);
  return {
    startedAt,
    expired,
    restart: () => {
      setExpired(false);
      setStartedAt(Date.now());
    },
  };
}
