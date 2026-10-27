# Boot 4 compatibility evidence

Synthetic fixtures originate from baseline 4197fbe and the verified Boot 3.5.16 / Jackson 2.21.8 / Security 6.5.11 artifacts. They contain no real customer data or private keys.

- `event-contracts/src/test/resources/compat/boot3/`: every one of the 25 v1 event types, including nulls, nested lists, UUIDs, microsecond Instants and decimal precision. The same JSON is stored as outbox payloads; processed-event inboxes store metadata columns rather than JSON payloads.
- Platform ApiError, product response and durable cart item fixtures: previous plain JSON encodings; tests deserialize with Jackson 3 and read new output with Jackson 2.
- Product typed Redis catalog fixtures retain @class, decimal and immutable-list wrappers. The Jackson 3 serializer explicitly validates allowed DTO/collection/decimal types, rejects unapproved classes and proves both directions against the old codec. No Redis namespace change or flush.
- Cart integration test seeds an old hash with unchanged metadata fields in isolated Redis, reads it, mutates through the current Lua path and verifies Jackson 2 can read the new item and its incremented version.
- `auth-service/.../compat/boot3`: RS256 access token and public JWK produced by Security 6; fixed-clock test preserves signature, issuer, kid, roles, session_id, expiry and required claims. No signing private key is committed. The boot4 token/public JWK are independently decoded by the preserved Security 6 runtime; see migration report.
- OAuth authorization request is a synthetic JDK-serialized Security 6 session attribute. Security 7 reads it and regression test verifies preserved fields; an independent Security 6 process reads the Security 7 rewrite. This covers the persisted handshake request; actual external provider/browser callbacks remain an acceptance check.
- Eight `compat/boot3-schema.sql` files: pg_dump --inserts of isolated schemas migrated with the original Flyway 11.7.2, retaining schema history/checksums. Synthetic business records and JSON/decimal/time probes are added to these test snapshots. Main flow tests normally use fresh databases. `-Dorderflow.verifyBoot3Schema=true` restores snapshots, reads existing business entities with Hibernate 7 (operations uses JDBC) before the original cleanup, validates Flyway and ddl-auto=validate, then reruns the workflow tests. These fixtures must only be loaded into empty isolated test databases.

Historical Flyway SQL is unchanged. No migration/cutover changes application data formats. These tests are representative compatibility checks; they do not prove an arbitrary production dataset, a full mixed-version rolling cluster, external OAuth callbacks, or the full stack under load.

## Fixture SHA-256 manifest

```json
{
  "product-service/src/test/resources/compat/boot3-schema.sql": "5d85d748f9370b6a53522e593fc6de4bdaf112da70ec9f9b7d41f00d462d550b",
  "product-service/src/test/resources/compat/boot3/catalog-product.json": "f1b37559dc867f33dc7e7a4cf17603c5ae2da6601fbc3dd1ab321d32f0752dca",
  "product-service/src/test/resources/compat/boot3/ProductDetailResponse.json": "6c426f255c74fc41661b6125876e4cc60c6c9331cc37a37eb1c51ce059d95685",
  "product-service/src/test/resources/compat/boot3/catalog-categories.json": "ca2163f1f2d51f760c66eeea7915a34dd3da5b91542d1e6ca58cd45406377080",
  "platform-contracts/src/test/resources/compat/boot3/ApiError.json": "38c6031b039c00d41d79ded23126099ea9d754cb3050bad4f56d297af44bf8f0",
  "delivery-service/src/test/resources/compat/boot3-schema.sql": "e309caaf1f78b1ede50e3a8ac5ed878c7e213eae12a606118e1b56ed749728a1",
  "operations-service/src/test/resources/compat/boot3-schema.sql": "8ca003db0209f51a2535f89ce67b17eabc236fa8bc13a9c9445f0a8facf8ff92",
  "notification-service/src/test/resources/compat/boot3-schema.sql": "293033904d5c26c0f3614f630316390debf9505cb0ac3ac6d93a9f17f4431240",
  "payment-service/src/test/resources/compat/boot3-schema.sql": "28f86a4864a0457a39e06f29a32014f46185c5846ce22c208b2f875a7a0aeb61",
  "event-contracts/src/test/resources/compat/boot3/NOTIFICATION_DELIVERED.json": "d1343b63de72901821a710b93464d95d137fc85f355674b695e46495911ec539",
  "event-contracts/src/test/resources/compat/boot3/ORDER_CREATED.json": "00535b0e3708233995381f1dabd832690b4fc766fdf8290480c1e2687eece964",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_REFUNDED.json": "cf4a6380c5a0d057d983c292450be38a0ed0c5fb4142ee8d04f183155379b83f",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_SUCCEEDED.json": "6e041f6971c1c42eaa1d98587c4b7ca392ff35cf241bb92961ab312c0506bfca",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_CANCEL_REQUESTED.json": "0255d4609a5a88bddb5980f3c1967d661f87d3d9d924ac7b555ef75e4cc42e0c",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_REQUESTED.json": "2e59ad66d58283e04dcf32aac157ad59c64e2efd88019bd81908ada81ef99bc6",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_CANCELLED.json": "2bcc3d293458ac07590d5a0645c55b6f2a6900ccb984fc2006c967b47a5f7df7",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_DELIVERED.json": "4c11d0d8403deafea52e6733096c8f0e46417f25ff163c1c1c325747e27057f3",
  "event-contracts/src/test/resources/compat/boot3/INVENTORY_COMMITTED.json": "cbbc878d77ba83a33037c8d380bc2ec05bd7633862a1be5a1e8e798db0fddf0d",
  "event-contracts/src/test/resources/compat/boot3/INVENTORY_RESERVED.json": "ebb21b9922cba5a922cbbbb8856c30b707842c105731144516fdae52ef012d16",
  "event-contracts/src/test/resources/compat/boot3/INVENTORY_RESERVATION_FAILED.json": "55dbff1654623a339faed4838a2008e2723aba50b8834f712c27f3c2d711e050",
  "event-contracts/src/test/resources/compat/boot3/INVENTORY_COMMIT_REQUESTED.json": "48a3b2f6fd644ead3e6b78bab3a0f830cf4b9185dc4ed0a9ee852101ab18e00c",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_OUT_FOR_DELIVERY.json": "d52fef84398081033f706e37f3126616c9b334db45eb5f3f1e754f09c763c979",
  "event-contracts/src/test/resources/compat/boot3/ORDER_CANCEL_REQUESTED.json": "163d28ba4e5d742b94537440a6af9503a73cd5a1034d3d0f553809d90f02e184",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_FAILED.json": "f60bae892c60494974ada746d6e1b0b49708e190035a5fb99735f90c65cc8ab7",
  "event-contracts/src/test/resources/compat/boot3/NOTIFICATION_FAILED.json": "bc8c5bb7c0e7c1d29ec8e0c3a3c91a3de0e694ffceafdb6e517b681091f5633d",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_ASSIGNED.json": "b82696d071eceb594f3fee8c22de8f17def250985f1d9b8c0153ef1b8af991b7",
  "event-contracts/src/test/resources/compat/boot3/INVENTORY_RELEASED.json": "731669e1cbc2bf6920c14c5bd93d8c1760c2b414d454bec440d922feffc2e0c4",
  "event-contracts/src/test/resources/compat/boot3/NOTIFICATION_DISPATCH_REQUESTED.json": "f6edc1d430f683bfeed30f9d371d25d492c084f79bd45c15ec70bef9eb1afad2",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_CANCELLED.json": "884fa6fa2a7c46b1ac8480a1ecfeccffd9a74cec66c2d9bd033a259dafbd38a5",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_REQUESTED.json": "049e39e13ea1fad2ed78741bce58f8b0e30bb73ae0feb75ed3941dfad4142366",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_FAILED.json": "b8e55e8bd6f86c97036b93cc1f5bfe8bad7746051cbec820811bef95733e7649",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_PICKED_UP.json": "478bebca481319fe158c035f580694e56920294ccebe9acda6f2320b17e7d6ca",
  "event-contracts/src/test/resources/compat/boot3/PAYMENT_REFUND_FAILED.json": "3ff99232377e94e10300da6a8c76c378f5f2d17672bf008279b235230008ae77",
  "event-contracts/src/test/resources/compat/boot3/DELIVERY_CANCEL_REQUESTED.json": "c0a546e1337f298b48000b47aa4d88927c529e104f158fa76929470ebfe06753",
  "order-service/src/test/resources/compat/boot3-schema.sql": "b1d776521fcb4f0db36eca13900b002931e812eefcbda2248e096547ce1c4445",
  "cart-service/src/test/resources/compat/boot3/StoredCartItem.json": "9527c573fd95cca3c86cb262a31a92ec2b262f3122e3deba47bdb3d67ef300f6",
  "auth-service/src/test/resources/compat/boot3-schema.sql": "f8d653991c4422c171936e478c11283b0a6149ea5ac8a8b67df1d7d6f8318d2b",
  "auth-service/src/test/resources/compat/boot3/public-jwk.json": "14f114d689362c69b0c3c658c70e1bcd41dea9ba2c4ba54cb1175d24229d9a6a",
  "auth-service/src/test/resources/compat/boot3/oauth2-authorization-request.base64": "9e61d3344bee828c4815c7637cde34a36e85bf2cfb8b926d642ee9709f7f8d95",
  "auth-service/src/test/resources/compat/boot3/access-token.jwt": "c3965a0c9e6ee2dd0b7cb47d1c2d1eae4073a1e6187e80fca720c2b54ff4b149",
  "auth-service/src/test/resources/compat/boot4/public-jwk.json": "930a0e66ae6b1bcfeaa76ed05f492a1b23e1a445d365baea3fd76a15e0dc191b",
  "auth-service/src/test/resources/compat/boot4/oauth2-authorization-request.base64": "77bfca59be06b567fac4a1b4ffc74978366deec1ee97e13b83b184d312baadd1",
  "auth-service/src/test/resources/compat/boot4/access-token.jwt": "6d56d4e1ec84150b21918a099256c69afe53fb0d7dd6d837716a72e440626fdc",
  "inventory-service/src/test/resources/compat/boot3-schema.sql": "1386d13bc9b090db32ccfb83996f23c5d249e471ebc5a2eb347ce2d8f3c69774"
}
```
