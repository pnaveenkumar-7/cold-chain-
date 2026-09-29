package com.coldchain.app.data.crypto;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class MerkleVerifier_Factory implements Factory<MerkleVerifier> {
  @Override
  public MerkleVerifier get() {
    return newInstance();
  }

  public static MerkleVerifier_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MerkleVerifier newInstance() {
    return new MerkleVerifier();
  }

  private static final class InstanceHolder {
    private static final MerkleVerifier_Factory INSTANCE = new MerkleVerifier_Factory();
  }
}
