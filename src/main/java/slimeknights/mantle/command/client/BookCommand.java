package slimeknights.mantle.command.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiBannerResultRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiBookModelRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiProfilerChartRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiSkinRenderState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import org.apache.commons.lang3.text.WordUtils;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.command.GeneratePackHelper;
import slimeknights.mantle.command.MantleCommand;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/** A command for different book operations, currently open and export_images */
public class BookCommand {
  private static final String BOOK_NOT_FOUND = "command.mantle.book_test.not_found";

  private static final String EXPORT_SUCCESS = "command.mantle.book.export.success";
  private static final String EXPORT_SUCCESS_HTML = "command.mantle.book.export.html.success";
  private static final String EXPORT_FAIL = "command.mantle.book.export.error_generic";
  private static final String EXPORT_FAIL_IO = "command.mantle.book.export.error_io";

  private static final String DEFAULT_BOOK_VERSION = "20";
  private static final String VERSION_FULL = "1.20";
  private static final int DEFAULT_SCALE = 2;

  /**
   * Registers this sub command with the root command
   * @param subCommand  Command builder
   */
  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand) {
    subCommand.requires(source -> MantleCommand.PERMISSION_GAME_COMMANDS.check(source.permissions()) && source.getEntity() instanceof AbstractClientPlayer)
      .then(Commands.literal("open")
        .then(Commands.argument("id", IdentifierArgument.id()).suggests(MantleClientCommand.REGISTERED_BOOKS)
          .executes(BookCommand::openBook)))

      .then(Commands.literal("export_images")
        // mantle book export_images <domain> [version]
        .then(Commands.argument("domain", StringArgumentType.word()).suggests(MantleClientCommand.REGISTERED_BOOK_DOMAINS)
          .then(Commands.argument("scale", IntegerArgumentType.integer(1, 16))
            .executes(context -> exportDomainImages(context, IntegerArgumentType.getInteger(context, "scale"))))
          .executes(context -> exportDomainImages(context, DEFAULT_SCALE)))
        // mantle book export_images <domain> [version]
        .then(Commands.argument("id", IdentifierArgument.id()).suggests(MantleClientCommand.REGISTERED_BOOKS)
          .then(Commands.argument("scale", IntegerArgumentType.integer(1, 16))
            .executes(context -> exportImages(context, IntegerArgumentType.getInteger(context, "scale"))))
          .executes(context -> exportImages(context, DEFAULT_SCALE))))

      .then(Commands.literal("export_html")
        // mantle book export_html <domain> [version]
        .then(Commands.argument("domain", StringArgumentType.word()).suggests(MantleClientCommand.REGISTERED_BOOK_DOMAINS)
          .then(Commands.argument("version", StringArgumentType.word())
            .executes(context -> exportDomainHtml(context, StringArgumentType.getString(context, "version"))))
          .executes(context -> exportDomainHtml(context, DEFAULT_BOOK_VERSION)))
        // mantle book export_html <id> [version]
        .then(Commands.argument("id", IdentifierArgument.id()).suggests(MantleClientCommand.REGISTERED_BOOKS)
          .then(Commands.argument("book_key", StringArgumentType.word())
            .executes(context -> exportHTML(context, StringArgumentType.getString(context, "book_key"))))
          .executes(context -> exportHTML(context, ""))));
  }

  /**
   * Opens the specified book
   * @param context  Command context
   * @return  Integer return
   */
  private static int openBook(CommandContext<CommandSourceStack> context) {
    Identifier book = IdentifierArgument.getId(context, "id");

    BookData bookData = BookLoader.getBook(book);
    if(bookData != null) {
      // Delay execution to ensure chat window is closed
      Minecraft.getInstance().execute(() ->
        bookData.openGui(Component.literal("Book"), "", null, null)
      );
    } else {
      bookNotFound(book);
      return 1;
    }

    return 0;
  }

  /**
   * Renders all images in the book to files
   * @param context  Command context
   * @return  Integer return
   */
  private static int exportImages(CommandContext<CommandSourceStack> context, int scale) {
    Identifier book = IdentifierArgument.getId(context, "id");
    return doExport(book, scale, false, DEFAULT_BOOK_VERSION);
  }

  /**
   * Renders all images in the books in the given domain to files
   * @param context  Command context
   * @return  Integer return
   */
  private static int exportDomainImages(CommandContext<CommandSourceStack> context, int scale) {
    String domain = StringArgumentType.getString(context, "domain");
    for (Identifier book : BookLoader.getAllBooks()) {
      if (domain.equals(book.getNamespace())) {
        int code = doExport(book, scale, false, DEFAULT_BOOK_VERSION);
        if (code != 0) return code;
      }
    }
    return 0;
  }

  /**
   * Exports all pages in the book to HTML and png
   * @param context Command context
   * @return Integer return
   */
  private static int exportHTML(CommandContext<CommandSourceStack> context, String bookKey) {
    Identifier book = IdentifierArgument.getId(context, "id");
    // if not specified, use book ID as the book key
    if (bookKey.isEmpty()) {
      bookKey = book.getPath() + '_' + DEFAULT_BOOK_VERSION;
    }
    return doExport(book, 2, true, bookKey);
  }

  /**
   * Exports all pages in all books to HTML and png
   * @param context Command context
   * @return Integer return
   */
  private static int exportDomainHtml(CommandContext<CommandSourceStack> context, String version) {
    String domain = StringArgumentType.getString(context, "domain");
    for (Identifier book : BookLoader.getAllBooks()) {
      if (domain.equals(book.getNamespace())) {
        int code = doExport(book, 2, true, book.getPath() + '_' + version);
        if (code != 0) return code;
      }
    }
    return 0;
  }

  /**
   * Renders all images in the book to files
   * @param book  Book to export
   * @param scale  Scale to export at
   * @param html  Include HTML
   * @param bookKey  Key of the book to use when exporting it in the header.
   * @return  Integer return
   */
  private static int doExport(Identifier book, int scale, boolean html, String bookKey) {
    BookData bookData = BookLoader.getBook(book);

    Minecraft minecraft = Minecraft.getInstance();
    Path gameDirectory = minecraft.gameDirectory.toPath();
    // images go in screenshots
    Path screenshotDir = Paths.get(gameDirectory.toString(), Screenshot.SCREENSHOT_DIR, "mantle_book", book.getNamespace(), book.getPath());
    // html goes in root folder
    Path htmlDir = html ? Paths.get(gameDirectory.toString(), "mantle_book", book.getNamespace(), book.getPath().replace('_', '-')) : null;
    if (bookData != null) {
      // ensure outputs exist
      if (!screenshotDir.toFile().mkdirs() && !screenshotDir.toFile().exists()) {
        throw new RuntimeException(Component.translatable(EXPORT_FAIL_IO, screenshotDir.toString()).getString());
      }
      if (htmlDir != null && !htmlDir.toFile().mkdirs() && !htmlDir.toFile().exists()) {
        throw new RuntimeException(Component.translatable(EXPORT_FAIL_IO, htmlDir.toString()).getString());
      }

      int width = BookScreen.PAGE_WIDTH_UNSCALED * 2 * scale;
      int height = BookScreen.PAGE_HEIGHT_UNSCALED * scale;

      bookData.load();
      BookScreen screen = new BookScreen(Component.literal("Book"), bookData, "", null, null);
      screen.init(width / scale, height / scale);
      screen.drawArrows = false;
      screen.mouseInput = false;
      screen.drawText = !html;
      screen.enableAnimations = false;

      // GUI rendering is split into an extract phase and a render phase since 1.21.6, and the GUI renderer always draws to the main render target.
      // So pages are extracted into our own GUI render state and rendered into the top left corner of the main target at the requested scale,
      // with each page copied out of the target before the next one is drawn. The main target is restored afterwards.
      GameRenderer gameRenderer = minecraft.gameRenderer;
      RenderTarget target = gameRenderer.mainRenderTarget();
      WindowRenderState window = gameRenderer.gameRenderState().windowRenderState;
      int oldTargetWidth = target.width;
      int oldTargetHeight = target.height;
      int oldWindowWidth = window.width;
      int oldWindowHeight = window.height;
      int oldGuiScale = window.guiScale;
      if (target.width < width || target.height < height) {
        target.resize(Math.max(target.width, width), Math.max(target.height, height));
      }
      // GUI projection uses the window size and GUI scale, so make one GUI pixel match the export scale
      window.width = target.width;
      window.height = target.height;
      window.guiScale = scale;
      GuiRenderState renderState = new GuiRenderState();
      GuiRenderer guiRenderer = new GuiRenderer(renderState, gameRenderer.featureRenderDispatcher(), pictureInPictureRenderers(minecraft));
      PageExports exports = new PageExports(screenshotDir, htmlDir);

      try {
        // title goes export title -> regular title -> path
        String exportTitle = bookData.appearance.exportTitle;
        if (exportTitle.isEmpty()) {
          exportTitle = bookData.appearance.title;
          if (exportTitle.isEmpty()) {
            exportTitle = WordUtils.capitalize(book.getPath().replace('_', ' '));
          }
        }

        // fetch mod display name if possible
        ModContainer mod = ModList.get().getModContainerById(book.getNamespace()).orElse(null);
        String modName = mod == null ? book.getNamespace() : mod.getModInfo().getDisplayName();

        do {
          screen.tick();

          int page = screen.getPage_();
          // draw text on the cover; we just want it as an image
          if (html) {
            screen.drawText = page < 0;
          }

          // extract and render the page, cleared to transparent so the image keeps transparency
          GpuTexture colorTexture = Objects.requireNonNull(target.getColorTexture(), "Main render target missing color texture");
          RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTexture, GuiRenderer.CLEAR_COLOR, Objects.requireNonNull(target.getDepthTexture()), 0.0);
          GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, renderState, 0, 0);
          screen.extractRenderState(graphics, 0, 0, 0);
          gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
          gameRenderer.useUiLightmap = true;
          try {
            guiRenderer.render();
            guiRenderer.endFrame();
          } finally {
            gameRenderer.useUiLightmap = false;
          }

          // copy the page out of the target, the image is saved once the GPU finishes the copy
          String pageFormat = page < 0 ? "cover" :  (html ? "clean_" : "page_") + page;
          Path path = Paths.get(screenshotDir.toString(), pageFormat + ".png");
          exports.save(colorTexture, target.height, width, height, page == -1, path);

          if (html) {
            File file = Paths.get(htmlDir.toString(), page < 0 ? "index.html" : "page-" + page + ".html").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
              writer.write(page < 0 ? screen.coverToHtml(bookKey, exportTitle, VERSION_FULL, modName) : screen.pageToHtml(bookKey, exportTitle, VERSION_FULL, modName));
            } catch (IOException e) {
              Mantle.logger.error("Failed to export HTML", e);
              throw new RuntimeException(Component.translatable(EXPORT_FAIL).getString());
            }
          }
        } while (screen.nextPage());

        // add gallery page
        if (html) {
          File file = Paths.get(htmlDir.toString(), "gallery.html").toFile();
          try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(galleryHtml(bookKey, exportTitle, modName));
          } catch (IOException e) {
            Mantle.logger.error("Failed to export HTML", e);
            throw new RuntimeException(Component.translatable(EXPORT_FAIL).getString());
          }
        }
      } catch (RuntimeException e) {
        // do not report success for a partial export
        exports.failed = true;
        throw e;
      } finally {
        guiRenderer.close();
        window.width = oldWindowWidth;
        window.height = oldWindowHeight;
        window.guiScale = oldGuiScale;
        if (target.width != oldTargetWidth || target.height != oldTargetHeight) {
          target.resize(oldTargetWidth, oldTargetHeight);
        }
        exports.finish();
      }
    } else {
      bookNotFound(book);
      return 1;
    }
    return 0;
  }

  /** Creates the picture in picture renderers for the export GUI renderer, matching the ones the game renderer uses */
  private static List<PictureInPictureRendererRegistration<?>> pictureInPictureRenderers(Minecraft minecraft) {
    return ClientHooks.gatherPictureInPictureRenderers(List.of(
      new PictureInPictureRendererRegistration<>(GuiEntityRenderState.class, () -> new GuiEntityRenderer(minecraft.getEntityRenderDispatcher())),
      new PictureInPictureRendererRegistration<>(GuiSkinRenderState.class, GuiSkinRenderer::new),
      new PictureInPictureRendererRegistration<>(GuiBookModelRenderState.class, GuiBookModelRenderer::new),
      new PictureInPictureRendererRegistration<>(GuiBannerResultRenderState.class, () -> new GuiBannerResultRenderer(minecraft.getAtlasManager())),
      new PictureInPictureRendererRegistration<>(GuiProfilerChartRenderState.class, GuiProfilerChartRenderer::new)
    ));
  }

  /** Tracks page images being copied from the GPU, sending the success message once the last one is saved */
  private static class PageExports {
    private final Path screenshotDir;
    @Nullable
    private final Path htmlDir;
    /** Number of pages still waiting on the GPU */
    private int pending = 0;
    /** Set once all pages are queued */
    private boolean finished = false;
    /** Set if any page failed to save */
    private boolean failed = false;

    private PageExports(Path screenshotDir, @Nullable Path htmlDir) {
      this.screenshotDir = screenshotDir;
      this.htmlDir = htmlDir;
    }

    /**
     * Copies a page from the top left corner of the texture and saves it once the copy completes.
     * Like {@link Screenshot#takeScreenshot(RenderTarget, java.util.function.Consumer)}, but keeps transparency and only copies the page region.
     * @param texture        Texture to copy from
     * @param textureHeight  Full height of the texture, as textures are stored bottom up
     * @param width          Width of the page region
     * @param height         Height of the page region
     * @param cover          If true, this is the cover which is half the width
     * @param path           Path to save the image
     */
    private void save(GpuTexture texture, int textureHeight, int width, int height, boolean cover, Path path) {
      int blockSize = texture.getFormat().blockSize();
      GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Mantle book export", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long)width * height * blockSize);
      pending++;
      RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
        try (GpuBufferSlice.MappedView read = buffer.map(true, false); NativeImage image = new NativeImage(width, height, false)) {
          // rows are stored bottom up, so flip while reading
          for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
              image.setPixelABGR(x, height - y - 1, read.data().getInt((x + y * width) * blockSize));
            }
          }
          if (cover) {
            // the cover is half the width
            try (NativeImage scaled = new NativeImage(image.format(), width / 2, height, false)) {
              image.copyRect(scaled, width / 2 - width / 4, 0, 0, 0, width / 2, height, false, false);
              scaled.writeToFile(path);
            }
          } else {
            image.writeToFile(path);
          }
        } catch (Exception e) {
          Mantle.logger.error("Failed to save screenshot {}", path, e);
          failed = true;
        } finally {
          buffer.close();
          pending--;
          sendMessageIfDone();
        }
      }, 0, 0, textureHeight - height, width, height);
    }

    /** Marks all pages as queued */
    private void finish() {
      finished = true;
      sendMessageIfDone();
    }

    /** Sends the result message once all pages are saved */
    private void sendMessageIfDone() {
      if (finished && pending == 0) {
        if (failed) {
          Player player = Minecraft.getInstance().player;
          if (player != null) {
            player.sendSystemMessage(Component.translatable(EXPORT_FAIL).withStyle(ChatFormatting.RED));
          }
        } else {
          sendFileMessage(screenshotDir, htmlDir);
        }
      }
    }
  }


  /** Creates the gallery HTML page */
  private static String galleryHtml(String bookName, String title, String mod) {
    return "---\n" +
      "layout: book-gallery\n" +
      "title: " + title + " (" + VERSION_FULL + ") - Gallery" + '\n' +
      "breadcrumb: Gallery\n" +
      "description: Gallery of all pages for " + title + " from " + mod + " in Minecraft " + VERSION_FULL + ".\n" +
      "book: " + bookName + '\n' +
      "link_prefix: ../\n" +
      "link_suffix: /gallery\n" +
      "---\n\n";
  }

  /** Send a message to the player linking the directory */
  private static void sendFileMessage(Path screenshotDir, @Nullable Path htmlDir) {
    Player player = Minecraft.getInstance().player;
    if (player != null) {
      Component fileComponent = GeneratePackHelper.getOutputComponent(screenshotDir);
      if (htmlDir != null) {
        player.sendSystemMessage(Component.translatable(EXPORT_SUCCESS_HTML, fileComponent, GeneratePackHelper.getOutputComponent(htmlDir)));
      } else {
        player.sendSystemMessage(Component.translatable(EXPORT_SUCCESS, fileComponent));
      }
    }
  }

  public static void bookNotFound(Identifier book) {
    Player player = Minecraft.getInstance().player;
    if (player != null) {
      player.sendSystemMessage(Component.translatable(BOOK_NOT_FOUND, book.toString()).withStyle(ChatFormatting.RED));
    }
  }
}
