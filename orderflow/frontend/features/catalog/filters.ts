import Decimal from "decimal.js";
export const productSorts = [
  ["createdAt,desc", "Newest arrivals"],
  ["price,asc", "Price: low to high"],
  ["price,desc", "Price: high to low"],
  ["name,asc", "Name: A–Z"],
  ["name,desc", "Name: Z–A"],
] as const;
export interface CatalogFilters {
  q: string;
  category: string;
  minPrice: string;
  maxPrice: string;
  sort: string;
  page: number;
}
export function catalogFilters(params: URLSearchParams): CatalogFilters {
  const requestedPage = Number(params.get("page") ?? 0);
  const sort = params.get("sort") ?? "createdAt,desc";
  return {
    q: (params.get("q") ?? "").trim(),
    category: (params.get("category") ?? "").trim(),
    minPrice: params.get("minPrice") ?? "",
    maxPrice: params.get("maxPrice") ?? "",
    sort: productSorts.some(([value]) => value === sort)
      ? sort
      : "createdAt,desc",
    page:
      Number.isSafeInteger(requestedPage) &&
      requestedPage >= 0 &&
      requestedPage < 100000
        ? requestedPage
        : 0,
  };
}
export function catalogQuery(filters: CatalogFilters) {
  return { ...filters, size: 12 };
}
export function validatePrices(min: string, max: string): string | null {
  try {
    if (min && (!/^\d+(\.\d+)?$/.test(min) || new Decimal(min).isNegative()))
      return "Minimum price must be a non-negative amount.";
    if (max && (!/^\d+(\.\d+)?$/.test(max) || new Decimal(max).isNegative()))
      return "Maximum price must be a non-negative amount.";
    if (min && max && new Decimal(min).greaterThan(max))
      return "Minimum price must not exceed maximum price.";
    return null;
  } catch {
    return "Enter valid price amounts.";
  }
}
export function catalogUrl(filters: CatalogFilters) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== "" && (key !== "page" || value !== 0))
      params.set(key, String(value));
  });
  return "/products" + (params.size ? "?" + params.toString() : "");
}
