package slimeknights.mantle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.HashCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import slimeknights.mantle.Mantle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Generic logic to convert any serializable object into JSON.
 * TODO 1.19: move to {@link slimeknights.mantle.datagen}
 */
@SuppressWarnings("unused")  // API
@RequiredArgsConstructor
@Log4j2
public abstract class GenericDataProvider implements DataProvider {
  private static final Gson GSON = (new GsonBuilder())
    .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();

  protected final DataGenerator generator;
  private final PackType type;
  private final String folder;
  private final Gson gson;

  public GenericDataProvider(DataGenerator generator, PackType type, String folder) {
    this(generator, type, folder, GSON);
  }

  public GenericDataProvider(DataGenerator generator, String folder, Gson gson) {
    this(generator, PackType.SERVER_DATA, folder, gson);
  }

  public GenericDataProvider(DataGenerator generator, String folder) {
    this(generator, folder, GSON);
  }

  protected void saveThing(HashCache cache, ResourceLocation location, Object materialJson) {
    try {
      String json = gson.toJson(materialJson);
      Path path = this.generator.getOutputFolder().resolve(Paths.get(type.getDirectory(), location.getNamespace(), folder, location.getPath() + ".json"));
      String hash = SHA1.hashUnencodedChars(json).toString();
      if (!Objects.equals(cache.getHash(path), hash) || !Files.exists(path)) {
        Files.createDirectories(path.getParent());

        try (BufferedWriter bufferedwriter = Files.newBufferedWriter(path)) {
          bufferedwriter.write(json);
        }
      }

      cache.putNew(path, hash);
    } catch (IOException e) {
      log.error("Couldn't create data for {}", location, e);
    }
  }

  /**
   * Saves the given object to JSON using a codec
   * @param output     Output for writing
   * @param location   Location relative to this data provider's root
   * @param codec      Codec to save the object
   * @param object     Object to save, will be converted using the passed codec
   */
  protected <T> void saveJson(HashCache output, ResourceLocation location, Codec<T> codec, T object) {
    saveThing(output, location, codec.encodeStart(JsonOps.INSTANCE, object).getOrThrow(false, Mantle.logger::error));
  }
}
