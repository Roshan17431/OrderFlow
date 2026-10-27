package com.orderflow.delivery.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.orderflow.delivery.config.DeliveryProperties;
import com.orderflow.delivery.domain.*;
import com.orderflow.delivery.repository.*;
import com.orderflow.events.ShippingAddressSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
class AgentAddressPrivacyTest {
 @Test void ownAgentProfileExposesItsAssignmentIdentifier(){
  UUID user=UUID.randomUUID(),profile=UUID.randomUUID();
  var deliveries=mock(DeliveryRepository.class);var agents=mock(DeliveryAgentRepository.class);var agent=mock(DeliveryAgentProfile.class);
  when(agents.findByUserId(user)).thenReturn(Optional.of(agent));when(agent.getId()).thenReturn(profile);
  when(agent.getAvailability()).thenReturn(DeliveryAgentAvailability.AVAILABLE);
  when(deliveries.findByDeliveryAgentId(eq(profile),any(org.springframework.data.domain.Pageable.class)))
    .thenReturn(new org.springframework.data.domain.PageImpl<Delivery>(List.of()));
  var query=new DeliveryQueryService(deliveries,agents,mock(DeliveryStatusHistoryRepository.class),new DeliveryMapper(),mock(DeliveryProperties.class));
  var body=tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().findAndAddModules().build().valueToTree(query.agent(user));
  assertThat(body.hasNonNull("profileId")).isTrue();assertThat(body.get("profileId").asText()).isEqualTo(profile.toString());
 }
 @Test void ownerWithAgentRoleCannotReadAddressUnlessAssigned(){
  UUID user=UUID.randomUUID(),id=UUID.randomUUID(),agentId=UUID.randomUUID();
  var deliveries=mock(DeliveryRepository.class);var agents=mock(DeliveryAgentRepository.class);var delivery=mock(Delivery.class);var agent=mock(DeliveryAgentProfile.class);
  when(deliveries.findById(id)).thenReturn(Optional.of(delivery));when(agents.findByUserId(user)).thenReturn(Optional.of(agent));when(agent.getId()).thenReturn(agentId);
  when(delivery.getCustomerId()).thenReturn(user);when(delivery.getDeliveryAgentId()).thenReturn(UUID.randomUUID());
  var query=new DeliveryQueryService(deliveries,agents,mock(DeliveryStatusHistoryRepository.class),new DeliveryMapper(),mock(DeliveryProperties.class));
  assertThatThrownBy(()->query.agentAddress(user,id)).isInstanceOf(com.orderflow.delivery.error.DeliveryDomainException.class);
  when(delivery.getDeliveryAgentId()).thenReturn(agentId);when(delivery.address()).thenReturn(new ShippingAddressSnapshot("Recipient","Street",null,"City","Region","12345","US","Side entrance"));
  assertThat(query.agentAddress(user,id).addressLine1()).isEqualTo("Street");
  assertThat(query.agentAddress(user,id).deliveryInstructions()).isEqualTo("Side entrance");
 }
}
