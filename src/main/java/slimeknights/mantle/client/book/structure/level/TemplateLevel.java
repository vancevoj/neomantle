// Credit to Immersive Engineering and blusunrize for this class
// See: https://github.com/BluSunrize/ImmersiveEngineering/blob/1.18/src/main/java/blusunrize/immersiveengineering/common/util/fakeworld/TemplateWorld.java
package slimeknights.mantle.client.book.structure.level;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.neoforged.neoforge.attachment.SessionData;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.model.data.ModelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * World implementation for the book structures.
 * Also acts as the {@link BlockAndTintGetter} used to tesselate the structure in {@link slimeknights.mantle.client.book.structure.StructurePreviewRenderer}.
 */
public class TemplateLevel extends Level implements BlockAndTintGetter {
  /** Lighting without directional face shading, matches the structure preview of older versions */
  private static final CardinalLighting FLAT_LIGHTING = new CardinalLighting(1, 1, 1, 1, 1, 1);

  private final Map<MapId, MapItemSavedData> maps = new HashMap<>();
  private final Scoreboard scoreboard = new Scoreboard();
  private final TickRateManager tickRateManager = new TickRateManager();
  private final WorldBorder worldBorder = new WorldBorder();
  /** Client level this template was created in, used for systems a fake level cannot provide on its own */
  private final ClientLevel parent;
  private final TemplateChunkSource chunkSource;

  public TemplateLevel(List<StructureBlockInfo> blocks, Predicate<BlockPos> shouldShow) {
    this(Objects.requireNonNull(Minecraft.getInstance().level), blocks, shouldShow);
  }

  private TemplateLevel(ClientLevel parent, List<StructureBlockInfo> blocks, Predicate<BlockPos> shouldShow) {
    super(
      new FakeLevelData(), Level.OVERWORLD, parent.registryAccess(),
      parent.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD),
      true, false, 0, 0
    );
    this.parent = parent;
    this.chunkSource = new TemplateChunkSource(blocks, this, shouldShow);
  }

  @Override
  public void sendBlockUpdated(@Nonnull BlockPos pos, @Nonnull BlockState oldState, @Nonnull BlockState newState, int flags) {}

  @Override
  public void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}

  @Override
  public void playSeededSound(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}

  @Override
  public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire,
                      ExplosionInteraction interactionType, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles,
                      WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> explosionSound) {}

  @Override
  public String gatherChunkSourceStats() {
    return chunkSource.gatherStats();
  }

  @Override
  public void setRespawnData(LevelData.RespawnData respawnData) {
    this.levelData.setSpawn(respawnData);
  }

  @Override
  public LevelData.RespawnData getRespawnData() {
    return this.levelData.getRespawnData();
  }

  @Nullable
  @Override
  public Entity getEntity(int id) {
    return null;
  }

  @Override
  public Collection<? extends PartEntity<?>> dragonParts() {
    return List.of();
  }

  @Nullable
  @Override
  public MapItemSavedData getMapData(@Nonnull MapId mapName) {
    return this.maps.get(mapName);
  }

  /** Stores map data in this level. No longer overrides vanilla, as map storage moved to the server level. */
  public void setMapData(MapId mapId, MapItemSavedData mapDataIn) {
    this.maps.put(mapId, mapDataIn);
  }

  /** Gets a free map ID in this level. No longer overrides vanilla, as map storage moved to the server level. */
  public MapId getFreeMapId() {
    return new MapId(this.maps.size());
  }

  @Override
  public void destroyBlockProgress(int breakerId, @Nonnull BlockPos pos, int progress) {}

  @Nonnull
  @Override
  public Scoreboard getScoreboard() {
    return this.scoreboard;
  }

  /** Recipes are not stored in the template, so use the recipes known to the client level */
  @Nonnull
  @Override
  public RecipeAccess recipeAccess() {
    return this.parent.recipeAccess();
  }

  @Nonnull
  @Override
  public TickRateManager tickRateManager() {
    return this.tickRateManager;
  }

  @Override
  protected LevelEntityGetter<Entity> getEntities() {
    return FakeEntityGetter.INSTANCE;
  }

  /** World clocks in the template follow the client level */
  @Override
  public ClockManager clockManager() {
    return this.parent.clockManager();
  }

  /** Environment attributes in the template follow the client level */
  @Override
  public EnvironmentAttributeSystem environmentAttributes() {
    return this.parent.environmentAttributes();
  }

  /** A fake level has no session of its own, so use the session of the client level */
  @Override
  public SessionData getSessionData() {
    return this.parent.getSessionData();
  }

  @Nonnull
  @Override
  public LevelTickAccess<Block> getBlockTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Nonnull
  @Override
  public LevelTickAccess<Fluid> getFluidTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Nonnull
  @Override
  public ChunkSource getChunkSource() {
    return this.chunkSource;
  }

  @Override
  public void levelEvent(@Nullable Entity source, int type, @Nonnull BlockPos pos, int data) {}

  @Override
  public void gameEvent(Holder<GameEvent> event, Vec3 position, Context context) {}

  @Override
  public FeatureFlagSet enabledFeatures() {
    return this.parent.enabledFeatures();
  }

  @Override
  public int getSeaLevel() {
    return this.parent.getSeaLevel();
  }

  @Override
  public WorldBorder getWorldBorder() {
    return this.worldBorder;
  }

  @Nonnull
  @Override
  public List<? extends Player> players() {
    return List.of();
  }

  @Nonnull
  @Override
  public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
    return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
  }


  /* Rendering */

  @Override
  public CardinalLighting cardinalLighting() {
    return FLAT_LIGHTING;
  }

  @Override
  public int getBlockTint(BlockPos pos, ColorResolver color) {
    return color.getColor(this.getBiome(pos).value(), pos.getX(), pos.getZ());
  }

  @Override
  public ModelData getModelData(BlockPos pos) {
    BlockEntity blockEntity = this.getBlockEntity(pos);
    return blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
  }
}
