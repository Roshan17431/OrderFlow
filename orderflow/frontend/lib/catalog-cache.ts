import type { QueryClient } from "@tanstack/react-query";

// Public listings, detail pages and category selectors use distinct query keys.
export function invalidateCatalog(client: QueryClient, categories = false) {
  return Promise.all([
    client.invalidateQueries({ queryKey: ["catalog"] }),
    client.invalidateQueries({ queryKey: ["product"] }),
    ...(categories
      ? [
          client.invalidateQueries({ queryKey: ["categories", "public"] }),
          client.invalidateQueries({ queryKey: ["products", "categories"] }),
        ]
      : []),
  ]);
}
