# Backend API integration matrix

Inspected current source at HEAD 3598ac8. Browser uses configured API Gateway only. Every mutation uses credentials and X-XSRF-TOKEN; protected calls add memory bearer access token. ApiError includes status, code, message, correlationId, fieldErrors; map 400/401/403/404/409/413/429/500/503; 429 honors Retry-After. Orders/payments/refunds use Idempotency-Key, reuse for an uncertain identical attempt.

| Source/controller | HTTP and actual endpoint | Role | Request/response contract | Frontend |
|---|---|---|---|---|
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | GET `/api/auth/csrf` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | POST `/api/auth/register` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | POST `/api/auth/login` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | POST `/api/auth/refresh` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | POST `/api/auth/logout` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | POST `/api/auth/logout-all` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/AuthController.java` | GET `/api/auth/me` | Public / authenticated me/logout | See `AuthController` method and sibling DTOs below | /login, /register, /account |
| `auth-service/src/main/java/com/orderflow/auth/controller/OAuthExchangeController.java` | POST `/api/auth/oauth2/exchange` | Public + CSRF | See `OAuthExchangeController` method and sibling DTOs below | /oauth2/success |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | GET `/api/cart` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | POST `/api/cart/items` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | PATCH `/api/cart/items/{productId}` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | DELETE `/api/cart/items/{productId}` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | DELETE `/api/cart` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `cart-service/src/main/java/com/orderflow/cart/controller/CartController.java` | POST `/api/cart/validate` | Authenticated own cart | See `CartController` method and sibling DTOs below | /cart, /checkout |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/AdminDeliveryController.java` | POST `/api/admin/deliveries/{id}/assign` | ADMIN | See `AdminDeliveryController` method and sibling DTOs below | /admin/deliveries |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/AdminDeliveryController.java` | GET `/api/admin/deliveries/{id}/history` | ADMIN | See `AdminDeliveryController` method and sibling DTOs below | /admin/deliveries |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | PUT `/api/delivery/agents/me/availability` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | GET `/api/delivery/agents/me` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | GET `/api/delivery/agents/me/deliveries` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | GET `/api/delivery/agents/me/deliveries/{id}` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | POST `/api/delivery/agents/me/deliveries/{id}/pickup` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | POST `/api/delivery/agents/me/deliveries/{id}/out-for-delivery` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | POST `/api/delivery/agents/me/deliveries/{id}/delivered` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryAgentController.java` | POST `/api/delivery/agents/me/deliveries/{id}/failed` | DELIVERY_AGENT own | See `DeliveryAgentController` method and sibling DTOs below | /delivery-agent/* |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryController.java` | GET `/api/deliveries/orders/{orderId}` | Owner / assigned agent / ADMIN | See `DeliveryController` method and sibling DTOs below | /orders/[id]/tracking, /admin/deliveries |
| `delivery-service/src/main/java/com/orderflow/delivery/controller/DeliveryController.java` | GET `/api/deliveries/{id}` | Owner / assigned agent / ADMIN | See `DeliveryController` method and sibling DTOs below | /orders/[id]/tracking, /admin/deliveries |
| `inventory-service/src/main/java/com/orderflow/inventory/controller/InventoryController.java` | GET `/api/inventory/products/{productId}` | VENDOR / ADMIN; reservation ADMIN | See `InventoryController` method and sibling DTOs below | /vendor/inventory/* |
| `inventory-service/src/main/java/com/orderflow/inventory/controller/InventoryController.java` | POST `/api/inventory/products/{productId}/adjustments` | VENDOR / ADMIN; reservation ADMIN | See `InventoryController` method and sibling DTOs below | /vendor/inventory/* |
| `inventory-service/src/main/java/com/orderflow/inventory/controller/InventoryController.java` | GET `/api/inventory/products/{productId}/adjustments` | VENDOR / ADMIN; reservation ADMIN | See `InventoryController` method and sibling DTOs below | /vendor/inventory/* |
| `inventory-service/src/main/java/com/orderflow/inventory/controller/InventoryController.java` | GET `/api/inventory/reservations/order/{orderId}` | VENDOR / ADMIN; reservation ADMIN | See `InventoryController` method and sibling DTOs below | /vendor/inventory/* |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | GET `/api/notifications` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | GET `/api/notifications/unread-count` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | PATCH `/api/notifications/{id}/read` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | PATCH `/api/notifications/read-all` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | GET `/api/notifications/preferences` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | PUT `/api/notifications/preferences` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `notification-service/src/main/java/com/orderflow/notification/controller/NotificationController.java` | GET `/api/notifications/stream` | Authenticated own | See `NotificationController` method and sibling DTOs below | /notifications, /settings/notifications, bell |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | GET `/api/admin/operations/overview` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | GET `/api/admin/operations/reports/daily` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | GET `/api/admin/operations/reports/daily/{date}` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | GET `/api/admin/operations/incidents` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | PATCH `/api/admin/operations/incidents/{incidentId}/resolve` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `operations-service/src/main/java/com/orderflow/operations/controller/OperationsController.java` | GET `/api/admin/operations/jobs` | ADMIN | See `OperationsController` method and sibling DTOs below | /admin/operations/* |
| `order-service/src/main/java/com/orderflow/order/controller/AdminOrderController.java` | GET `/api/admin/orders` | ADMIN | See `AdminOrderController` method and sibling DTOs below | /admin/orders/* |
| `order-service/src/main/java/com/orderflow/order/controller/OrderController.java` | POST `/api/orders` | Authenticated owned / ADMIN | See `OrderController` method and sibling DTOs below | /checkout, /orders/* |
| `order-service/src/main/java/com/orderflow/order/controller/OrderController.java` | GET `/api/orders` | Authenticated owned / ADMIN | See `OrderController` method and sibling DTOs below | /checkout, /orders/* |
| `order-service/src/main/java/com/orderflow/order/controller/OrderController.java` | GET `/api/orders/{orderId}` | Authenticated owned / ADMIN | See `OrderController` method and sibling DTOs below | /checkout, /orders/* |
| `order-service/src/main/java/com/orderflow/order/controller/OrderController.java` | GET `/api/orders/{orderId}/history` | Authenticated owned / ADMIN | See `OrderController` method and sibling DTOs below | /checkout, /orders/* |
| `order-service/src/main/java/com/orderflow/order/controller/OrderController.java` | POST `/api/orders/{orderId}/cancel` | Authenticated owned / ADMIN | See `OrderController` method and sibling DTOs below | /checkout, /orders/* |
| `payment-service/src/main/java/com/orderflow/payment/controller/AdminPaymentController.java` | GET `/api/admin/payments` | ADMIN | See `AdminPaymentController` method and sibling DTOs below | /admin/payments/* |
| `payment-service/src/main/java/com/orderflow/payment/controller/AdminPaymentController.java` | GET `/api/admin/payments/{paymentId}` | ADMIN | See `AdminPaymentController` method and sibling DTOs below | /admin/payments/* |
| `payment-service/src/main/java/com/orderflow/payment/controller/AdminPaymentController.java` | POST `/api/admin/payments/{paymentId}/refunds` | ADMIN | See `AdminPaymentController` method and sibling DTOs below | /admin/payments/* |
| `payment-service/src/main/java/com/orderflow/payment/controller/AdminPaymentController.java` | GET `/api/admin/payments/{paymentId}/refunds` | ADMIN | See `AdminPaymentController` method and sibling DTOs below | /admin/payments/* |
| `payment-service/src/main/java/com/orderflow/payment/controller/PaymentController.java` | GET `/api/payments/orders/{orderId}` | Authenticated owned / ADMIN | See `PaymentController` method and sibling DTOs below | /orders/[id] |
| `payment-service/src/main/java/com/orderflow/payment/controller/PaymentController.java` | GET `/api/payments/{paymentId}` | Authenticated owned / ADMIN | See `PaymentController` method and sibling DTOs below | /orders/[id] |
| `payment-service/src/main/java/com/orderflow/payment/controller/PaymentController.java` | GET `/api/payments/{paymentId}/attempts` | Authenticated owned / ADMIN | See `PaymentController` method and sibling DTOs below | /orders/[id] |
| `payment-service/src/main/java/com/orderflow/payment/controller/PaymentController.java` | POST `/api/payments/orders/{orderId}/confirm` | Authenticated owned / ADMIN | See `PaymentController` method and sibling DTOs below | /orders/[id] |
| `payment-service/src/main/java/com/orderflow/payment/controller/PaymentController.java` | POST `/api/payments/{paymentId}/cancel` | Authenticated owned / ADMIN | See `PaymentController` method and sibling DTOs below | /orders/[id] |
| `payment-service/src/main/java/com/orderflow/payment/controller/SimulatorWebhookController.java` | POST `/api/payments/webhooks/simulator` | Signed provider only, trusted CLI | See `SimulatorWebhookController` method and sibling DTOs below | No browser endpoint |
| `product-service/src/main/java/com/orderflow/product/controller/AdminCategoryController.java` | POST `/api/admin/categories` | ADMIN | See `AdminCategoryController` method and sibling DTOs below | /admin/categories |
| `product-service/src/main/java/com/orderflow/product/controller/AdminCategoryController.java` | PUT `/api/admin/categories/{categoryId}` | ADMIN | See `AdminCategoryController` method and sibling DTOs below | /admin/categories |
| `product-service/src/main/java/com/orderflow/product/controller/AdminCategoryController.java` | PATCH `/api/admin/categories/{categoryId}/activate` | ADMIN | See `AdminCategoryController` method and sibling DTOs below | /admin/categories |
| `product-service/src/main/java/com/orderflow/product/controller/AdminCategoryController.java` | PATCH `/api/admin/categories/{categoryId}/deactivate` | ADMIN | See `AdminCategoryController` method and sibling DTOs below | /admin/categories |
| `product-service/src/main/java/com/orderflow/product/controller/CategoryController.java` | GET `/api/categories` | Public | See `CategoryController` method and sibling DTOs below | /products |
| `product-service/src/main/java/com/orderflow/product/controller/CategoryController.java` | GET `/api/categories/{slug}/products` | Public | See `CategoryController` method and sibling DTOs below | /products |
| `product-service/src/main/java/com/orderflow/product/controller/PublicCatalogController.java` | GET `/api/products` | Public; resolve authenticated | See `PublicCatalogController` method and sibling DTOs below | /, /products, /products/[id] |
| `product-service/src/main/java/com/orderflow/product/controller/PublicCatalogController.java` | GET `/api/products/{productId}` | Public; resolve authenticated | See `PublicCatalogController` method and sibling DTOs below | /, /products, /products/[id] |
| `product-service/src/main/java/com/orderflow/product/controller/PublicCatalogController.java` | GET `/api/products/slug/{slug}` | Public; resolve authenticated | See `PublicCatalogController` method and sibling DTOs below | /, /products, /products/[id] |
| `product-service/src/main/java/com/orderflow/product/controller/PublicCatalogController.java` | POST `/api/products/resolve` | Public; resolve authenticated | See `PublicCatalogController` method and sibling DTOs below | /, /products, /products/[id] |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | POST `/api/vendor/products` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | PUT `/api/vendor/products/{productId}` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | PATCH `/api/vendor/products/{productId}/activate` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | PATCH `/api/vendor/products/{productId}/deactivate` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | PATCH `/api/vendor/products/{productId}/archive` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | GET `/api/vendor/products` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |
| `product-service/src/main/java/com/orderflow/product/controller/VendorProductController.java` | GET `/api/vendor/products/{productId}/management-access` | VENDOR / ADMIN | See `VendorProductController` method and sibling DTOs below | /vendor/products/* |

## Source DTO definitions

The following fields/validation are copied from current Java records, rather than inferred from older docs. Public product responses do not expose stock. Public categories contain only active categories; vendor product list supports page/size/sort, not a q/status filter. Page responses use items in product/inventory/order and content in payment/delivery/notification/operations; frontend normalizes at the client boundary. Operations incident maps use SQL snake_case; reports/jobs use their explicit aliased camelCase keys.

### `auth-service/src/main/java/com/orderflow/auth/dto/request/LoginRequest.java`

```java
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 128) String password)
```

### `auth-service/src/main/java/com/orderflow/auth/dto/request/OAuthExchangeRequest.java`

```java
public record OAuthExchangeRequest(@NotBlank @Size(max = 256) String code)
```

### `auth-service/src/main/java/com/orderflow/auth/dto/request/RegisterRequest.java`

```java
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 100) String displayName)
```

### `auth-service/src/main/java/com/orderflow/auth/dto/response/AuthenticationResponse.java`

```java
public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user)
```

### `auth-service/src/main/java/com/orderflow/auth/dto/response/CsrfResponse.java`

```java
public record CsrfResponse(String headerName, String parameterName, String token)
```

### `auth-service/src/main/java/com/orderflow/auth/dto/response/UserResponse.java`

```java
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        List<String> roles,
        Instant createdAt)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/AddCartItemRequest.java`

```java
public record AddCartItemRequest(@NotNull UUID productId, @Min(1) int quantity)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/CartItemResponse.java`

```java
public record CartItemResponse(
        UUID productId,
        String productNameSnapshot,
        int quantity,
        BigDecimal unitPriceSnapshot,
        String currency,
        Long productVersionSnapshot,
        boolean purchasable,
        boolean priceChanged,
        Instant addedAt,
        Instant updatedAt)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/CartResponse.java`

```java
public record CartResponse(
        UUID userId,
        long version,
        String currency,
        List<CartItemResponse> items,
        int distinctItemCount,
        int totalQuantity,
        BigDecimal subtotal,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/CartValidationIssue.java`

```java
public record CartValidationIssue(UUID productId, String code, String message)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/CartValidationResponse.java`

```java
public record CartValidationResponse(
        CartResponse cart, boolean valid, List<CartValidationIssue> issues)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/ProductResolutionItem.java`

```java
public record ProductResolutionItem(
        UUID productId,
        String name,
        BigDecimal priceAmount,
        String currency,
        boolean purchasable,
        Long productVersion)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/ProductResolutionRequest.java`

```java
public record ProductResolutionRequest(List<UUID> productIds)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/ProductResolutionResponse.java`

```java
public record ProductResolutionResponse(List<ProductResolutionItem> items)
```

### `cart-service/src/main/java/com/orderflow/cart/dto/UpdateCartItemRequest.java`

```java
public record UpdateCartItemRequest(@Min(1) int quantity)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/AgentResponse.java`

```java
public record AgentResponse(UUID userId,DeliveryAgentAvailability availability,UUID activeDeliveryId,Instant updatedAt)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/AssignDeliveryRequest.java`

```java
public record AssignDeliveryRequest(@NotNull UUID deliveryAgentId)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/AvailabilityRequest.java`

```java
public record AvailabilityRequest(@NotNull DeliveryAgentAvailability availability)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/DeliveryAddressResponse.java`

```java
public record DeliveryAddressResponse(String recipientName,String city,String stateOrRegion,String countryCode)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/DeliveryResponse.java`

```java
public record DeliveryResponse(UUID id,UUID orderId,DeliveryStatus status,UUID deliveryAgentId,DeliveryAddressResponse shippingAddress,Instant assignedAt,Instant pickedUpAt,Instant outForDeliveryAt,Instant deliveredAt,Instant failedAt,Instant cancelledAt,long version)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/FailureRequest.java`

```java
public record FailureRequest(@NotBlank @Pattern(regexp="CUSTOMER_UNAVAILABLE|ADDRESS_PROBLEM|PACKAGE_DAMAGED|VEHICLE_PROBLEM|OTHER") String failureCode)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/HistoryResponse.java`

```java
public record HistoryResponse(DeliveryStatus previousStatus,DeliveryStatus newStatus,UUID actorUserId,UUID sourceEventId,String correlationId,Instant changedAt)
```

### `delivery-service/src/main/java/com/orderflow/delivery/dto/PageResponse.java`

```java
public record PageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/InventoryAdjustmentResponse.java`

```java
public record InventoryAdjustmentResponse(
        UUID id,
        UUID productId,
        long quantityDelta,
        long resultingOnHand,
        long resultingReserved,
        String reason,
        String reference,
        UUID actorUserId,
        Instant createdAt,
        String correlationId)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/InventoryStockResponse.java`

```java
public record InventoryStockResponse(
        UUID productId,
        long onHandQuantity,
        long reservedQuantity,
        long availableQuantity,
        long version,
        Instant updatedAt)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/PageResponse.java`

```java
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/ReservationItemResponse.java`

```java
public record ReservationItemResponse(UUID productId, long quantity)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/ReservationResponse.java`

```java
public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt,
        Instant releasedAt,
        List<ReservationItemResponse> items,
        long version)
```

### `inventory-service/src/main/java/com/orderflow/inventory/dto/StockAdjustmentRequest.java`

```java
public record StockAdjustmentRequest(
        @NotNull Long quantityDelta,
        @NotBlank @Size(max = 120) String reason,
        @Size(max = 200) String reference)
```

### `notification-service/src/main/java/com/orderflow/notification/dto/NotificationResponse.java`

```java
public record NotificationResponse(UUID id,NotificationType type,String title,String message,boolean read,Instant readAt,Instant createdAt)
```

### `notification-service/src/main/java/com/orderflow/notification/dto/PageResponse.java`

```java
public record PageResponse<T>(List<T>content,int page,int size,long totalElements,int totalPages)
```

### `notification-service/src/main/java/com/orderflow/notification/dto/PreferenceRequest.java`

```java
public record PreferenceRequest(boolean emailEnabled,@Size(max=254)String emailAddress,boolean smsEnabled,@Size(max=32)String phoneNumber)
```

### `notification-service/src/main/java/com/orderflow/notification/dto/PreferenceResponse.java`

```java
public record PreferenceResponse(boolean emailEnabled,String emailAddress,boolean smsEnabled,String phoneNumber,Instant updatedAt,long version)
```

### `notification-service/src/main/java/com/orderflow/notification/dto/UnreadCountResponse.java`

```java
public record UnreadCountResponse(long count)
```

### `operations-service/src/main/java/com/orderflow/operations/dto/PageResponse.java`

```java
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
```

### `operations-service/src/main/java/com/orderflow/operations/dto/ResolutionRequest.java`

```java
public record ResolutionRequest(@NotBlank @Size(max = 300) String resolutionNote)
```

### `order-service/src/main/java/com/orderflow/order/dto/CancelOrderRequest.java`

```java
public record CancelOrderRequest(@NotBlank @Size(max = 200) String reason)
```

### `order-service/src/main/java/com/orderflow/order/dto/CreateOrderRequest.java`

```java
public record CreateOrderRequest(
        @Min(0) long expectedCartVersion,
        @Valid @NotNull ShippingAddressRequest shippingAddress)
```

### `order-service/src/main/java/com/orderflow/order/dto/OrderHistoryResponse.java`

```java
public record OrderHistoryResponse(
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String reasonCode,
        UUID eventId,
        String correlationId,
        Instant changedAt)
```

### `order-service/src/main/java/com/orderflow/order/dto/OrderItemResponse.java`

```java
public record OrderItemResponse(
        UUID productId,
        String productName,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currency,
        Long productVersion)
```

### `order-service/src/main/java/com/orderflow/order/dto/OrderResponse.java`

```java
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        ShippingAddressResponse shippingAddress,
        String currency,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        long cartVersion,
        String cancellationReason,
        UUID inventoryReservationId,
        Instant inventoryReservationExpiresAt,
        UUID paymentId,
        Instant paidAt,
        Instant paymentFailedAt,
        String paymentFailureCode,
        BigDecimal refundedAmount,
        Instant refundedAt,
        Instant inventoryReleasedAt,
        UUID deliveryId,
        UUID deliveryAgentId,
        Instant deliveryRequestedAt,
        Instant deliveryAssignedAt,
        Instant pickedUpAt,
        Instant outForDeliveryAt,
        Instant deliveredAt,
        Instant deliveryFailedAt,
        Instant deliveryCancelledAt,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version)
```

### `order-service/src/main/java/com/orderflow/order/dto/PageResponse.java`

```java
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last)
```

### `order-service/src/main/java/com/orderflow/order/dto/ShippingAddressRequest.java`

```java
public record ShippingAddressRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p
```

### `order-service/src/main/java/com/orderflow/order/dto/ShippingAddressResponse.java`

```java
public record ShippingAddressResponse(String recipientName, String addressLine1, String addressLine2,
        String city, String stateOrRegion, String postalCode, String countryCode, String deliveryInstructions)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/AdminPaymentDetailResponse.java`

```java
public record AdminPaymentDetailResponse(
        PaymentResponse payment,
        List<PaymentAttemptResponse> attempts,
        List<RefundResponse> refunds)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/CancelPaymentRequest.java`

```java
public record CancelPaymentRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "[A-Z][A-Z0-9_]*")
        String reason)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/ConfirmPaymentRequest.java`

```java
public record ConfirmPaymentRequest(
        @NotBlank(message = "paymentMethodToken is required")
        @Size(max = 128)
        String paymentMethodToken)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/CreateRefundRequest.java`

```java
public record CreateRefundRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2)
        BigDecimal amount,
        @NotBlank @Pattern(regexp =
                "CUSTOMER_REQUEST|DUPLICATE_PAYMENT|ORDER_CANCELLED|SERVICE_FAILURE|SYSTEM_COMPENSATION|OTHER")
        String reason)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/PageResponse.java`

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/PaymentAttemptResponse.java`

```java
public record PaymentAttemptResponse(
        int attemptNumber,
        PaymentAttemptStatus status,
        String failureCode,
        Instant createdAt,
        Instant completedAt)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/PaymentResponse.java`

```java
public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        BigDecimal refundedAmount,
        String failureCode,
        Instant paymentDeadline,
        Instant createdAt,
        Instant updatedAt,
        Instant succeededAt,
        Instant failedAt,
        Instant cancelledAt)
```

### `payment-service/src/main/java/com/orderflow/payment/dto/RefundResponse.java`

```java
public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String reason,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        Instant succeededAt,
        Instant failedAt)
```

### `product-service/src/main/java/com/orderflow/product/dto/CategoryListResponse.java`

```java
public record CategoryListResponse(List<CategoryResponse> items)
```

### `product-service/src/main/java/com/orderflow/product/dto/CategoryResponse.java`

```java
public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version)
```

### `product-service/src/main/java/com/orderflow/product/dto/CategorySummary.java`

```java
public record CategorySummary(UUID id, String name, String slug)
```

### `product-service/src/main/java/com/orderflow/product/dto/CreateCategoryRequest.java`

```java
public record CreateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 120) String slug,
        @Size(max = 1000) String description)
```

### `product-service/src/main/java/com/orderflow/product/dto/CreateProductRequest.java`

```java
public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal priceAmount,
        @Size(min = 3, max = 3) String currency,
        @NotNull UUID categoryId,
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 120) String slug,
        UUID vendorId)
```

### `product-service/src/main/java/com/orderflow/product/dto/PageResponse.java`

```java
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductDetailResponse.java`

```java
public record ProductDetailResponse(
        UUID id,
        String slug,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        CategorySummary category,
        Instant createdAt,
        Instant updatedAt,
        long version)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductManagementAccessResponse.java`

```java
public record ProductManagementAccessResponse(
        UUID productId, ProductStatus status, UUID vendorId)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductManagementResponse.java`

```java
public record ProductManagementResponse(
        UUID id,
        UUID vendorId,
        String sku,
        String slug,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        ProductStatus status,
        CategorySummary category,
        Instant createdAt,
        Instant updatedAt,
        long version)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductResolutionItem.java`

```java
public record ProductResolutionItem(
        UUID productId,
        String name,
        BigDecimal priceAmount,
        String currency,
        boolean purchasable,
        Long productVersion)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductResolutionRequest.java`

```java
public record ProductResolutionRequest(
        @NotEmpty @Size(max = 100) List<UUID> productIds)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductResolutionResponse.java`

```java
public record ProductResolutionResponse(List<ProductResolutionItem> items)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductSearchCriteria.java`

```java
public record ProductSearchCriteria(
        String query,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency,
        String sort,
        int page,
        int size)
```

### `product-service/src/main/java/com/orderflow/product/dto/ProductSummary.java`

```java
public record ProductSummary(
        UUID id,
        String slug,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        CategorySummary category,
        Instant updatedAt)
```

### `product-service/src/main/java/com/orderflow/product/dto/UpdateCategoryRequest.java`

```java
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
                @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
                @Size(max = 120)
                String slug,
        @Size(max = 1000) String description,
        @PositiveOrZero long version)
```

### `product-service/src/main/java/com/orderflow/product/dto/UpdateProductRequest.java`

```java
public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal priceAmount,
        @Size(min = 3, max = 3) String currency,
        @NotNull UUID categoryId,
        @PositiveOrZero long version)
```

### `product-service/src/main/java/com/orderflow/product/dto/VersionRequest.java`

```java
public record VersionRequest(@PositiveOrZero long version)
```

## Current API gaps and supported alternatives

- Vendor has no single-product management detail GET: locate the requested product through its bounded vendor listing, with a finite page budget and explicit not-found/retry state. SKU/slug are immutable on edit (UpdateProductRequest).
- No public stock quantity: catalogue visibility determines purchasability; inventory rejection is communicated after checkout.
- No general admin delivery list or agent directory: list/lookup deliveries through authorized admin orders or entered order/delivery IDs; assign an explicitly entered agent identifier.
- Category GET is public active-only; management supports mutations but no inactive list. A minimal ADMIN-only list may be necessary to reactivate inactive categories.
- DeliveryResponse includes only recipient/city/region/country, even for assigned agents; full street/instructions require an ownership-protected additive response if added.
- No in-app preference toggle exists. In-app notifications are always durable; preference API controls simulated email/SMS only.
- NotificationResponse has no resource/order link; do not manufacture a link from message text.
- OAuth permitted by gateway security but authorization/callback paths have no route forwarding; a minimal route is required when real provider profile is configured.
- No safe browser simulator completion endpoint: CLI uses signed webhook; processing display remains truthful.
- Self-service role elevation is absent; fixture-only isolated user role provisioning is privileged trusted tooling.

## Phase 9 additive compatibility fixes

- ADMIN GET `/api/admin/categories?page=0&size=20`: CategoryManagementService.list, PageResponse<CategoryResponse> items/totalElements/totalPages; rejects size above100 and includes inactive entries.
- DELIVERY_AGENT GET `/api/delivery/agents/me/deliveries/{id}/address`: DeliveryQueryService.agentAddress, AgentDeliveryAddressResponse (full address); strictly matches agent profile ID to deliveryAgentId, even if caller owns that order as a customer. Other users see 404; ordinary customer delivery DTO stays masked.
- Gateway forwards `/oauth2/**` and `/login/oauth2/**` to auth-service; defaults point successful/failed frontend redirects at port3000. Provider configuration still required and disabled by default.
