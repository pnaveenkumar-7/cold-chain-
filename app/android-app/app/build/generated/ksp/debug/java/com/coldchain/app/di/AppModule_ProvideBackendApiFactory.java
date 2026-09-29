package com.coldchain.app.di;

import com.coldchain.app.data.api.BackendApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class AppModule_ProvideBackendApiFactory implements Factory<BackendApi> {
  private final Provider<Retrofit> retrofitProvider;

  public AppModule_ProvideBackendApiFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public BackendApi get() {
    return provideBackendApi(retrofitProvider.get());
  }

  public static AppModule_ProvideBackendApiFactory create(Provider<Retrofit> retrofitProvider) {
    return new AppModule_ProvideBackendApiFactory(retrofitProvider);
  }

  public static BackendApi provideBackendApi(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBackendApi(retrofit));
  }
}
