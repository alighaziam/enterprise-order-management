package io.github.alighaziam.eom.catalog;
import java.util.Optional;
import java.util.UUID;
public interface CatalogOperations { Optional<CatalogProduct> findActiveProduct(UUID productId); }
