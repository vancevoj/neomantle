package slimeknights.mantle.data.predicate.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

/** Predicate matching a single damage type */
public record DamageTypePredicate(ResourceKey<DamageType> type) implements DamageSourcePredicate {
  public static final RecordLoadable<DamageTypePredicate> LOADER = RecordLoadable.create(
    Loadables.RESOURCE_LOCATION.requiredField("name", p -> p.type.location()),
    DamageTypePredicate::new);

  public DamageTypePredicate(ResourceLocation id) {
    this(ResourceKey.create(Registries.DAMAGE_TYPE, id));
  }

  @Override
  public boolean matches(DamageSource input) {
    return input.is(type);
  }

  @Override
  public RecordLoadable<DamageTypePredicate> getLoader() {
    return LOADER;
  }
}
