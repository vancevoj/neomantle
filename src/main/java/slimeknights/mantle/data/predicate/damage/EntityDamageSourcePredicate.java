package slimeknights.mantle.data.predicate.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;

import javax.annotation.Nullable;

/**
 * Predicate matching damage sources containing entities with specific entity properties
 */
public record EntityDamageSourcePredicate(WhichEntity which, IJsonPredicate<LivingEntity> predicate) implements DamageSourcePredicate {
  public static final RecordLoadable<EntityDamageSourcePredicate> LOADER = RecordLoadable.create(
    new EnumLoadable<>(WhichEntity.class).defaultField("which", WhichEntity.CAUSING, true, EntityDamageSourcePredicate::which),
    LivingEntityPredicate.LOADER.directField("entity_type", EntityDamageSourcePredicate::predicate),
    EntityDamageSourcePredicate::new);

  public static EntityDamageSourcePredicate causing(IJsonPredicate<LivingEntity> predicate) {
    return new EntityDamageSourcePredicate(WhichEntity.CAUSING, predicate);
  }

  public static EntityDamageSourcePredicate direct(IJsonPredicate<LivingEntity> predicate) {
    return new EntityDamageSourcePredicate(WhichEntity.DIRECT, predicate);
  }

  @Override
  public boolean matches(DamageSource input) {
    Entity entity = which.get(input);
    if (entity instanceof LivingEntity living) {
      return predicate.matches(living);
    }
    return predicate == LivingEntityPredicate.ANY && entity != null;
  }

  @Override
  public RecordLoadable<EntityDamageSourcePredicate> getLoader() {
    return LOADER;
  }

  /** Which entity is being considered */
  private enum WhichEntity {
    CAUSING {
      @Override
      public Entity get(DamageSource source) {
        return source.getEntity();
      }
    },
    DIRECT {
      @Nullable
      @Override
      public Entity get(DamageSource source) {
        return source.getDirectEntity();
      }
    };

    @Nullable
    public abstract Entity get(DamageSource source);
  }
}
