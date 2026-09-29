package com.coldchain.app.ui.screens.sender;

import com.coldchain.app.data.repository.ShipmentRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class SenderViewModel_Factory implements Factory<SenderViewModel> {
  private final Provider<ShipmentRepository> repositoryProvider;

  public SenderViewModel_Factory(Provider<ShipmentRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public SenderViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static SenderViewModel_Factory create(Provider<ShipmentRepository> repositoryProvider) {
    return new SenderViewModel_Factory(repositoryProvider);
  }

  public static SenderViewModel newInstance(ShipmentRepository repository) {
    return new SenderViewModel(repository);
  }
}
