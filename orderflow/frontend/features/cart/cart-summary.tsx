import Decimal from "decimal.js";
import { money } from "@/lib/formatting";
import type { Cart } from "@/types/contracts";
import { Card } from "@/components/ui";
const ExactDecimal = Decimal.clone({ precision: 40 });
export function lineAmount(price: string | number, quantity: number) {
  return new ExactDecimal(price).times(quantity).toFixed(2);
}
export function CartSummary({
  cart,
  children,
}: {
  cart: Cart;
  children?: React.ReactNode;
}) {
  return (
    <Card title="Order summary">
      <div className="stack">
        {cart.items.map((item) => (
          <div className="summary-line" key={item.productId}>
            <span>
              {item.productNameSnapshot}
              <br />
              <span className="small muted">
                {item.quantity} × {money(item.unitPriceSnapshot, item.currency)}
              </span>
            </span>
            <span>
              {money(
                lineAmount(item.unitPriceSnapshot, item.quantity),
                item.currency,
              )}
            </span>
          </div>
        ))}
        <div className="summary-line">
          <strong>Subtotal</strong>
          <strong>
            {money(cart.subtotal, cart.currency ?? "USD")}{" "}
            <span className="small">{cart.currency}</span>
          </strong>
        </div>
        <p className="small muted">
          Final order totals are confirmed by OrderFlow when your order is
          created.
        </p>
        {children}
      </div>
    </Card>
  );
}
