package io.github.alighaziam.eom.catalog;
import java.math.BigDecimal;
import java.util.UUID;
public record CatalogProduct(UUID id,String sku,String name,BigDecimal unitPrice,String currency,boolean active){}
