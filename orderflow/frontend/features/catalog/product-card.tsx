"use client";
import Link from "next/link";
import { ArrowUpRight, Package } from "lucide-react";
import { money } from "@/lib/formatting";
import type { Product } from "@/types/contracts";
export function ProductArt({ large = false }: { large?: boolean }) {
  return (
    <div
      className="product-art"
      style={large ? { height: 400, borderRadius: 16 } : undefined}
      aria-hidden="true"
    >
      <Package size={large ? 130 : 72} strokeWidth={1} />
    </div>
  );
}
export function ProductCard({ product }: { product: Product }) {
  return (
    <article className="product-card">
      <Link
        href={"/products/" + product.id}
        aria-label={`View ${product.name}`}
        tabIndex={-1}
      >
        <ProductArt />
      </Link>
      <div className="product-body">
        <p className="eyebrow">{product.category.name}</p>
        <h3>
          <Link href={"/products/" + product.id}>{product.name}</Link>
        </h3>
        <div className="row justify-between">
          <span>
            {money(product.priceAmount, product.currency)}{" "}
            <span className="small muted">{product.currency}</span>
          </span>
          <Link
            href={"/products/" + product.id}
            aria-label={`Details for ${product.name}`}
          >
            <ArrowUpRight size={18} />
          </Link>
        </div>
      </div>
    </article>
  );
}
