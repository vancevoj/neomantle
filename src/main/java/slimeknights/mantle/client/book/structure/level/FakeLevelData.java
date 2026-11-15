// Credit to Immersive Engineering and blusunrize for this class
// See: https://github.com/BluSunrize/ImmersiveEngineering/blob/1.18/src/main/java/blusunrize/immersiveengineering/common/util/fakeworld/FakeSpawnInfo.java
package slimeknights.mantle.client.book.structure.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;

public class FakeLevelData implements WritableLevelData {

  private RespawnData respawnData = RespawnData.DEFAULT;

  @Override
  public void setSpawn(RespawnData respawnData) {
    this.respawnData = respawnData;
  }

  @Override
  public RespawnData getRespawnData() {
    return this.respawnData;
  }

  /** Sets the spawn position and angle, adapter for the former vanilla method */
  public void setSpawn(BlockPos spawn, float angle) {
    this.setSpawn(RespawnData.of(Level.OVERWORLD, spawn, angle, 0));
  }

  /** Gets the spawn position, adapter for the former vanilla method */
  public BlockPos getSpawnPos() {
    return this.respawnData.pos();
  }

  /** Gets the spawn angle, adapter for the former vanilla method */
  public float getSpawnAngle() {
    return this.respawnData.yaw();
  }

  @Override
  public long getGameTime() {
    return 0;
  }

  @Override
  public boolean isHardcore() {
    return false;
  }

  @Override
  public Difficulty getDifficulty() {
    return Difficulty.PEACEFUL;
  }

  @Override
  public boolean isDifficultyLocked() {
    return false;
  }
}
