package com.orderflow.product.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.orderflow.product.cache.CatalogVersionService;
import com.orderflow.product.domain.Category;
import com.orderflow.product.dto.CategoryResponse;
import com.orderflow.product.repository.*;
import com.orderflow.product.validation.SlugService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
class CategoryAdminListTest {
 @Test void managementListIncludesInactiveCategoriesInBoundedPages(){
  var repository=mock(CategoryRepository.class);var mapper=mock(ProductMapper.class);
  var category=Category.create(UUID.randomUUID(),"Office","office",null,Instant.now());category.deactivate(Instant.now());
  when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(category)));
  when(mapper.category(category)).thenReturn(new CategoryResponse(category.getId(),"Office","office",null,category.getStatus(),Instant.now(),Instant.now(),0));
  var service=new CategoryManagementService(repository,mock(ProductRepository.class),mapper,mock(SlugService.class),mock(CatalogVersionService.class),Clock.systemUTC());
  assertThat(service.list(0,20).items()).singleElement().satisfies(v->assertThat(v.status().name()).isEqualTo("INACTIVE"));
  verify(repository).findAll(PageRequest.of(0,20,Sort.by("name")));
  assertThatThrownBy(()->service.list(0,101)).isInstanceOf(com.orderflow.product.error.ProductDomainException.class);
 }
}
