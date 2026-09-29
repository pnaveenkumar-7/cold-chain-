package com.coldchain.app.ui.screens.inspect;

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
public final class PublicInspectViewModel_Factory implements Factory<PublicInspectViewModel> {
  private final Provider<ShipmentRepository> repositoryProvider;

  public PublicInspectViewModel_Factory(Provider<ShipmentRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public PublicInspectViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static PublicInspectViewModel_Factory create(
      Provider<ShipmentRepository> repositoryProvider) {
    return new PublicInspectViewModel_Factory(repositoryProvider);
  }

  public static PublicInspectViewModel newInstance(ShipmentRepository repository) {
    return new PublicInspectViewModel(repository);
  }
}
