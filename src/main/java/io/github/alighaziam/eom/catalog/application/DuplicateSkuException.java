package io.github.alighaziam.eom.catalog.application;
public class DuplicateSkuException extends RuntimeException {
 public DuplicateSkuException(String sku){super("A product with SKU already exists: "+sku);}
}