package com.coldchain.app.data.repository;

import com.coldchain.app.data.api.BackendApi;
import com.coldchain.app.data.blockchain.RpcClient;
import com.coldchain.app.data.crypto.MerkleVerifier;
import com.coldchain.app.data.local.AuthPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ShipmentRepository_Factory implements Factory<ShipmentRepository> {
  private final Provider<BackendApi> apiProvider;

  private final Provider<RpcClient> rpcClientProvider;

  private final Provider<MerkleVerifier> merkleVerifierProvider;

  private final Provider<AuthPreferences> authPreferencesProvider;

  public ShipmentRepository_Factory(Provider<BackendApi> apiProvider,
      Provider<RpcClient> rpcClientProvider, Provider<MerkleVerifier> merkleVerifierProvider,
      Provider<AuthPreferences> authPreferencesProvider) {
    this.apiProvider = apiProvider;
    this.rpcClientProvider = rpcClientProvider;
    this.merkleVerifierProvider = merkleVerifierProvider;
    this.authPreferencesProvider = authPreferencesProvider;
  }

  @Override
  public ShipmentRepository get() {
    return newInstance(apiProvider.get(), rpcClientProvider.get(), merkleVerifierProvider.get(), authPreferencesProvider.get());
  }

  public static ShipmentRepository_Factory create(Provider<BackendApi> apiProvider,
      Provider<RpcClient> rpcClientProvider, Provider<MerkleVerifier> merkleVerifierProvider,
      Provider<AuthPreferences> authPreferencesProvider) {
    return new ShipmentRepository_Factory(apiProvider, rpcClientProvider, merkleVerifierProvider, authPreferencesProvider);
  }

  public static ShipmentRepository newInstance(BackendApi api, RpcClient rpcClient,
      MerkleVerifier merkleVerifier, AuthPreferences authPreferences) {
    return new ShipmentRepository(api, rpcClient, merkleVerifier, authPreferences);
  }
}
