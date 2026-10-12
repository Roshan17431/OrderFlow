# OrderFlow visual system

Role-based commerce app: public storefront, customer shopping/cart/checkout/orders, vendor catalogue/inventory, agent deliveries and admin operations. Real backend data only; meaningful empty/error states. Primary style source: Mosaic Grid Architecture Style, adapted to the specified commerce use case.

Use warm white #faf9f6 background, #ffffff cards, #eceee5 muted blocks, ink #20261e, muted text #687062, forest/olive #4d633a primary, gold #cdbb8a restrained detail. Positive green, warning amber and destructive red with text labels. Body uses system sans-serif; large storefront heading uses Georgia serif. No remote fonts/images. Rounded 12-20px cards, fine #e0e3da borders, subtle shadows, generous 24/32px gaps and a 1200px max-width.

Storefront: quiet top assurance strip, OrderFlow wordmark and cube mark, Home/Shop links, search/login/cart; editorial hero with large concise headline, light sage visual built with abstract stacked product tiles and no stock imagery; category pills and actual product grid; clear footer. Dashboard: fixed dark forest sidebar, compact topbar, pale main canvas with breadcrumb, heading, KPI cards and readable tables. Mobile has collapsible nav and scrollable tables. Icons are Lucide outlines. All inputs/buttons have visible labels and focus; statuses readable without color. Animation restrained and respects reduced motion.

A storefront draft is a design reference only; example card text in the draft is design content, never production/API fixture behavior. Implementation loads backend products and shows empty states when none exist.
