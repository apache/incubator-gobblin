package org.apache.gobblin.test;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.apache.gobblin.test.generator.ValueGenerator;
import org.apache.gobblin.test.type.Type;


@AllArgsConstructor
public abstract class BaseValueGenerator<T> implements ValueGenerator<T> {
  @Getter
  private final Type type;
  protected ValueGenerator<T> underlyingGenerator;

  public Type getLogicalType() {
    return type;
  }

  public T get() {
    return this.underlyingGenerator.get();
  }
}
