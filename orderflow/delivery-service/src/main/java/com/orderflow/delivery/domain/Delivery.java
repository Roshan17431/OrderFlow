package com.orderflow.delivery.domain;

import com.orderflow.events.ShippingAddressSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "delivery")
public class Delivery {
    @Id private UUID id;
    @Column(name="order_id", nullable=false, unique=true) private UUID orderId;
    @Column(name="customer_id", nullable=false) private UUID customerId;
    @Column(name="delivery_agent_id") private UUID deliveryAgentId;
    @Column(name="recipient_name",nullable=false,length=120) private String recipientName;
    @Column(name="address_line_1",nullable=false,length=200) private String addressLine1;
    @Column(name="address_line_2",length=200) private String addressLine2;
    @Column(nullable=false,length=120) private String city;
    @Column(name="state_or_region",nullable=false,length=120) private String stateOrRegion;
    @Column(name="postal_code",nullable=false,length=32) private String postalCode;
    @Column(name="country_code",nullable=false,length=2) private String countryCode;
    @Column(name="delivery_instructions",length=300) private String deliveryInstructions;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private DeliveryStatus status;
    @Column(name="failure_code",length=80) private String failureCode;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="assigned_at") private Instant assignedAt;
    @Column(name="picked_up_at") private Instant pickedUpAt;
    @Column(name="out_for_delivery_at") private Instant outForDeliveryAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="failed_at") private Instant failedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Version private long version;
    protected Delivery() {}
    public static Delivery create(UUID orderId, UUID customerId, ShippingAddressSnapshot a, Instant now) {
        Delivery d=new Delivery(); d.id=UUID.randomUUID(); d.orderId=orderId; d.customerId=customerId;
        d.recipientName=a.recipientName();d.addressLine1=a.addressLine1();d.addressLine2=a.addressLine2();d.city=a.city();d.stateOrRegion=a.stateOrRegion();d.postalCode=a.postalCode();d.countryCode=a.countryCode();d.deliveryInstructions=a.deliveryInstructions();
        d.status=DeliveryStatus.PENDING_ASSIGNMENT;d.createdAt=now;d.updatedAt=now;return d;
    }
    public void assign(UUID agentId, Instant at){require(DeliveryStatus.PENDING_ASSIGNMENT);deliveryAgentId=agentId;status=DeliveryStatus.ASSIGNED;assignedAt=at;updatedAt=at;}
    public void pickup(Instant at){require(DeliveryStatus.ASSIGNED);status=DeliveryStatus.PICKED_UP;pickedUpAt=at;updatedAt=at;}
    public void outForDelivery(Instant at){require(DeliveryStatus.PICKED_UP);status=DeliveryStatus.OUT_FOR_DELIVERY;outForDeliveryAt=at;updatedAt=at;}
    public void delivered(Instant at){require(DeliveryStatus.OUT_FOR_DELIVERY);status=DeliveryStatus.DELIVERED;deliveredAt=at;updatedAt=at;}
    public void failed(String code, Instant at){if(!Set.of(DeliveryStatus.ASSIGNED,DeliveryStatus.PICKED_UP,DeliveryStatus.OUT_FOR_DELIVERY).contains(status))conflict();failureCode=code;status=DeliveryStatus.FAILED;failedAt=at;updatedAt=at;}
    public void cancel(String reason, Instant at){if(status==DeliveryStatus.OUT_FOR_DELIVERY||status==DeliveryStatus.DELIVERED)conflict();if(!Set.of(DeliveryStatus.PENDING_ASSIGNMENT,DeliveryStatus.ASSIGNED,DeliveryStatus.PICKED_UP,DeliveryStatus.CANCEL_REQUESTED).contains(status))conflict();failureCode=reason;status=DeliveryStatus.CANCELLED;cancelledAt=at;updatedAt=at;}
    private void require(DeliveryStatus s){if(status!=s)conflict();} private void conflict(){throw new IllegalStateException("Invalid delivery state transition");}
    public ShippingAddressSnapshot address(){return new ShippingAddressSnapshot(recipientName,addressLine1,addressLine2,city,stateOrRegion,postalCode,countryCode,deliveryInstructions);}
    public UUID getId(){return id;} public UUID getOrderId(){return orderId;} public UUID getCustomerId(){return customerId;} public UUID getDeliveryAgentId(){return deliveryAgentId;} public DeliveryStatus getStatus(){return status;} public String getFailureCode(){return failureCode;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getAssignedAt(){return assignedAt;} public Instant getPickedUpAt(){return pickedUpAt;} public Instant getOutForDeliveryAt(){return outForDeliveryAt;} public Instant getDeliveredAt(){return deliveredAt;} public Instant getFailedAt(){return failedAt;} public Instant getCancelledAt(){return cancelledAt;} public long getVersion(){return version;}
}
