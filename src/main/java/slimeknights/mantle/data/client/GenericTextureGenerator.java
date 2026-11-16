package slimeknights.mantle.data.client;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.GenericDataProvider;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Data generator to create png image files.
 * <p>
 * 26.3: {@code ExistingFileHelper} is gone, existing textures are read through the client resource manager from
 * {@link net.neoforged.neoforge.data.event.GatherDataEvent#getResourceManager(net.minecraft.server.packs.PackType)}.
 */
public abstract class GenericTextureGenerator extends GenericDataProvider {
  /** Resource manager for reading existing textures, may be null if the generator never reads textures */
  @Nullable
  protected final ResourceManager resourceManager;

  /**
   * Creates a new texture generator
   * @param packOutput       Pack output
   * @param resourceManager  Client resource manager for reading existing textures, from {@code event.getResourceManager(PackType.CLIENT_RESOURCES)}
   * @param folder           Folder for the generated textures
   */
  public GenericTextureGenerator(PackOutput packOutput, @Nullable ResourceManager resourceManager, String folder) {
    super(packOutput, Target.RESOURCE_PACK, folder);
    this.resourceManager = resourceManager;
  }

  /** Saves the given image to the given location */
  protected CompletableFuture<?> saveImage(CachedOutput cache, Identifier location, NativeImage image) {
    return saveImage(cache, pathProvider, location, image);
  }

  /** Saves metadata for the given image */
  protected CompletableFuture<?> saveMetadata(CachedOutput cache, Identifier location, JsonObject metadata) {
    return DataProvider.saveStable(cache, metadata, this.pathProvider.file(location, "png.mcmeta"));
  }


  /* Helpers */

  /** Reads an image from disk. Note the caller is responsible for closing the resource */
  public static NativeImage read(ResourceManager resourceManager, String folder, Identifier path) throws IOException {
    try {
      Resource resource = resourceManager.getResourceOrThrow(path.withPath(folder + "/" + path.getPath() + ".png"));
      try (InputStream stream = resource.open()) {
        return NativeImage.read(stream);
      }
    } catch (IOException | NoSuchElementException e) {
      Mantle.logger.error("Failed to read image at {}", path, e);
      throw e;
    }
  }

  /**
   * Encodes the given image as a PNG file. Replaces the removed {@code NativeImage#asByteArray()}.
   * @param image  Image to encode
   * @return  PNG file bytes
   * @throws IOException  If encoding fails
   */
  public static byte[] toPngBytes(NativeImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (STBIWriteCallback callback = STBIWriteCallback.create((context, data, size) -> {
      ByteBuffer buffer = STBIWriteCallback.getData(data, size);
      byte[] bytes = new byte[size];
      buffer.get(bytes);
      output.write(bytes, 0, size);
    })) {
      if (STBImageWrite.nstbi_write_png_to_func(callback.address(), 0L, image.getWidth(), image.getHeight(), image.format().components(), image.getPointer(), 0) == 0) {
        throw new IOException("Failed to encode PNG image: " + STBImage.stbi_failure_reason());
      }
    }
    return output.toByteArray();
  }

  /** Saves the given image to the given location */
  public static CompletableFuture<?> saveImage(CachedOutput cache, PathProvider pathProvider, Identifier location, NativeImage image) {
    return CompletableFuture.runAsync(() -> {
      try {
        Path path = pathProvider.file(location, "png");
        byte[] bytes = toPngBytes(image);
        cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes));
      } catch (IOException e) {
        Mantle.logger.error("Couldn't write image for {}", location, e);
        throw new CompletionException(e);
      }
    }, Util.backgroundExecutor());
  }
}
