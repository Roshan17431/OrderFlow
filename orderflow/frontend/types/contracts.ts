export type Money = string | number;
export type Role = "CUSTOMER" | "VENDOR" | "DELIVERY_AGENT" | "ADMIN";
export interface User {
  id: string;
  email: string;
  displayName: string;
  status: string;
  roles: Role[];
  createdAt: string;
}
export interface Authentication {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Category {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: "ACTIVE" | "INACTIVE";
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface CategorySummary {
  id: string;
  name: string;
  slug: string;
}
export interface Product {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  priceAmount: Money;
  currency: string;
  category: CategorySummary;
  updatedAt: string;
  createdAt?: string;
  version?: number;
}
export interface VendorProduct extends Product {
  vendorId: string;
  sku: string;
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";
  version: number;
}
export interface ProductInput {
  sku: string;
  name: string;
  description?: string;
  priceAmount: Money;
  currency: string;
  categoryId: string;
  slug?: string;
  vendorId?: string;
}
export type ProductUpdate = Omit<ProductInput, "sku" | "slug" | "vendorId"> & {
  version: number;
};
export interface CartItem {
  productId: string;
  productNameSnapshot: string;
  quantity: number;
  unitPriceSnapshot: Money;
  currency: string;
  productVersionSnapshot: number | null;
  purchasable: boolean;
  priceChanged: boolean;
  addedAt: string;
  updatedAt: string;
}
export interface Cart {
  userId: string;
  version: number;
  currency: string | null;
  items: CartItem[];
  distinctItemCount: number;
  totalQuantity: number;
  subtotal: Money;
  createdAt: string | null;
  updatedAt: string | null;
  expiresAt: string | null;
}
export interface CartValidation {
  cart: Cart;
  valid: boolean;
  issues: { productId: string; code: string; message: string }[];
}
export interface Address {
  recipientName: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  stateOrRegion: string;
  postalCode: string;
  countryCode: string;
  deliveryInstructions?: string | null;
}
export type OrderStatus =
  | "INVENTORY_PENDING"
  | "INVENTORY_RESERVED"
  | "INVENTORY_REJECTED"
  | "PAYMENT_PENDING"
  | "PAID"
  | "PAYMENT_FAILED"
  | "CANCEL_REQUESTED"
  | "CANCELLED"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED";
export type FulfillmentStatus =
  | "NOT_REQUESTED"
  | "DELIVERY_PENDING"
  | "DELIVERY_ASSIGNED"
  | "PICKED_UP"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "DELIVERY_FAILED"
  | "DELIVERY_CANCEL_REQUESTED"
  | "DELIVERY_CANCELLED";
export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: Money;
  lineTotal: Money;
  currency: string;
  productVersion: number | null;
}
export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  fulfillmentStatus: FulfillmentStatus;
  shippingAddress: Address;
  currency: string;
  subtotal: Money;
  totalAmount: Money;
  cartVersion: number;
  cancellationReason: string | null;
  inventoryReservationId: string | null;
  inventoryReservationExpiresAt: string | null;
  paymentId: string | null;
  paidAt: string | null;
  paymentFailedAt: string | null;
  paymentFailureCode: string | null;
  refundedAmount: Money;
  refundedAt: string | null;
  inventoryReleasedAt: string | null;
  deliveryId: string | null;
  deliveryAgentId: string | null;
  deliveryRequestedAt: string | null;
  deliveryAssignedAt: string | null;
  pickedUpAt: string | null;
  outForDeliveryAt: string | null;
  deliveredAt: string | null;
  deliveryFailedAt: string | null;
  deliveryCancelledAt: string | null;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface OrderHistory {
  previousStatus: OrderStatus | null;
  newStatus: OrderStatus;
  reasonCode: string;
  eventId: string | null;
  correlationId: string;
  changedAt: string;
}
export type PaymentStatus =
  | "PENDING"
  | "REQUIRES_ACTION"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCEL_REQUESTED"
  | "CANCELLED"
  | "REFUND_PENDING"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED";
export interface Payment {
  id: string;
  orderId: string;
  status: PaymentStatus;
  amount: Money;
  currency: string;
  provider: string;
  refundedAmount: Money;
  failureCode: string | null;
  paymentDeadline: string;
  createdAt: string;
  updatedAt: string;
  succeededAt: string | null;
  failedAt: string | null;
  cancelledAt: string | null;
}
export interface Refund {
  id: string;
  paymentId: string;
  amount: Money;
  currency: string;
  status: "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  reason: string;
  failureCode: string | null;
  createdAt: string;
  updatedAt: string;
  succeededAt: string | null;
  failedAt: string | null;
}
export interface PaymentAttempt {
  attemptNumber: number;
  status: string;
  failureCode: string | null;
  createdAt: string;
  completedAt: string | null;
}
export interface PaymentDetail {
  payment: Payment;
  attempts: PaymentAttempt[];
  refunds: Refund[];
}
export interface Stock {
  productId: string;
  onHandQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  version: number;
  updatedAt: string;
}
export interface Adjustment {
  id: string;
  productId: string;
  quantityDelta: number;
  resultingOnHand: number;
  resultingReserved: number;
  reason: string;
  reference: string | null;
  actorUserId: string;
  createdAt: string;
  correlationId: string;
}
export interface StockInput {
  quantityDelta: number;
  reason: string;
  reference?: string;
}
export type DeliveryStatus =
  | "PENDING_ASSIGNMENT"
  | "ASSIGNED"
  | "PICKED_UP"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "FAILED"
  | "CANCEL_REQUESTED"
  | "CANCELLED";
export interface Delivery {
  id: string;
  orderId: string;
  status: DeliveryStatus;
  deliveryAgentId: string | null;
  shippingAddress: {
    recipientName: string;
    city: string;
    stateOrRegion: string;
    countryCode: string;
  };
  assignedAt: string | null;
  pickedUpAt: string | null;
  outForDeliveryAt: string | null;
  deliveredAt: string | null;
  failedAt: string | null;
  cancelledAt: string | null;
  version: number;
}
export interface Agent {
  profileId?: string;
  userId: string;
  availability: "OFFLINE" | "AVAILABLE" | "BUSY";
  activeDeliveryId: string | null;
  updatedAt: string;
}
export interface DeliveryHistory {
  previousStatus: DeliveryStatus | null;
  newStatus: DeliveryStatus;
  actorUserId: string | null;
  sourceEventId: string | null;
  correlationId: string;
  changedAt: string;
}
export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}
export interface Preferences {
  emailEnabled: boolean;
  emailAddress: string | null;
  smsEnabled: boolean;
  phoneNumber: string | null;
  updatedAt?: string;
  version?: number;
}
export interface Overview {
  date: string;
  ordersCreated: number;
  paymentsSucceeded: number;
  paymentsFailed: number;
  paymentVolume: Money;
  inventoryReservationFailures: number;
  deliveriesCompleted: number;
  deliveriesFailed: number;
  openIncidents: number;
  deliveriesPendingAssignment: number;
  notificationsFailed: number;
}
export interface DailyReport {
  id: string;
  status: string;
  generatedAt: string;
  finalizedAt: string | null;
  reportDate: string;
  ordersCreated: number;
  paymentsSucceeded: number;
  paymentsFailed: number;
  paymentVolume: Money;
  refundVolume: Money;
  inventoryReservationFailures: number;
  deliveriesCompleted: number;
  deliveriesFailed: number;
  notificationsDelivered: number;
  notificationsFailed: number;
  version: number;
}
export interface Incident {
  id: string;
  incident_type: string;
  severity: string;
  source_service: string;
  reason_code: string;
  occurred_at: string;
  resolved: boolean;
  resolved_at: string | null;
  resolution_note: string | null;
}
export interface Job {
  jobName: string;
  lastStatus: string | null;
  lastStartedAt: string | null;
  lastCompletedAt: string | null;
  lastDurationMs: number | null;
  failureCount: number;
}
export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  correlationId?: string;
  fieldErrors?: { field: string; message: string }[];
}
export type QueryParams = Record<
  string,
  string | number | boolean | null | undefined
>;
