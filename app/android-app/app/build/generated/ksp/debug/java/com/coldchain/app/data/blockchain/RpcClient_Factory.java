package com.coldchain.app.data.blockchain;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class RpcClient_Factory implements Factory<RpcClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  public RpcClient_Factory(Provider<OkHttpClient> okHttpClientProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public RpcClient get() {
    return newInstance(okHttpClientProvider.get());
  }

  public static RpcClient_Factory create(Provider<OkHttpClient> okHttpClientProvider) {
    return new RpcClient_Factory(okHttpClientProvider);
  }

  public static RpcClient newInstance(OkHttpClient okHttpClient) {
    return new RpcClient(okHttpClient);
  }
}
