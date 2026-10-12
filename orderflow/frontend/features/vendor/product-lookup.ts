import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import type { VendorProduct } from "@/types/contracts";
// Management has no detail GET. Never discover products from the public catalogue.
export async function findVendorProduct(
  productId: string,
  vendorId: string,
): Promise<VendorProduct> {
  for (let page = 0; page < 20; page++) {
    const result = await api.vendor.list({
      vendorId,
      page,
      size: 100,
      sort: "createdAt,desc",
    });
    const product = result.items.find(
      (item) => item.id === productId && item.vendorId === vendorId,
    );
    if (product) return product;
    if (page + 1 >= result.totalPages)
      throw new ApiError(
        404,
        "PRODUCT_NOT_FOUND",
        "This product was not found in your product list. It may no longer be available to your account.",
      );
  }
  throw new ApiError(
    422,
    "PRODUCT_LOOKUP_LIMIT",
    "The product was not found in the first 2,000 products. Return to the paginated product list to locate it, or retry if the list changed.",
  );
}
