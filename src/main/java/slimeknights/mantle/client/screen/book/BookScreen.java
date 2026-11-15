package slimeknights.mantle.client.screen.book;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.data.PageData;
import slimeknights.mantle.client.book.data.SectionData;
import slimeknights.mantle.client.screen.book.element.BookElement;
import slimeknights.mantle.util.html.HtmlElement;
import slimeknights.mantle.util.html.HtmlSerializable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class BookScreen extends Screen {

  public static boolean debug = false;

  public static final int TEX_SIZE = 512;

  public static final int PAGE_MARGIN = 8;

  public static final int PAGE_PADDING_TOP = 4;
  public static final int PAGE_PADDING_BOT = 4;
  public static final int PAGE_PADDING_LEFT = 8;
  public static final int PAGE_PADDING_RIGHT = 0;

  public static final float PAGE_SCALE = 1f;
  public static final int PAGE_WIDTH_UNSCALED = 206;
  public static final int PAGE_HEIGHT_UNSCALED = 200;

  // For best results, make sure both PAGE_WIDTH_UNSCALED - (PAGE_PADDING + PAGE_MARGIN) * 2 and PAGE_HEIGHT_UNSCALED - (PAGE_PADDING + PAGE_MARGIN) * 2 divide evenly into PAGE_SCALE (without remainder)
  public static final int PAGE_WIDTH = (int) ((PAGE_WIDTH_UNSCALED - (PAGE_PADDING_LEFT + PAGE_PADDING_RIGHT + PAGE_MARGIN + PAGE_MARGIN)) / PAGE_SCALE);
  public static final int PAGE_HEIGHT = (int) ((PAGE_HEIGHT_UNSCALED - (PAGE_PADDING_TOP + PAGE_PADDING_BOT + PAGE_MARGIN + PAGE_MARGIN)) / PAGE_SCALE);

  /** Font with the uniform glyphs, formerly {@code Minecraft.UNIFORM_FONT} */
  public static final Identifier UNIFORM_FONT = Identifier.withDefaultNamespace("uniform");
  /** Font with the enchanting table glyphs, formerly {@code Minecraft.ALT_FONT} */
  public static final Identifier ALT_FONT = Identifier.withDefaultNamespace("alt");

  // Cached instance of Minecraft fonts
  @Nullable
  private static Font uniformFont;
  @Nullable
  private static Font altFont;

  /** If true, shows next and previous page buttons. Set to false during export. */
  public boolean drawArrows = true;
  /** If true, the mouse may be used to interact with book elements. Set to false during export. */
  public boolean mouseInput = true;
  /** If true, animated elements can animate. Set to false during export to ensure first element consistently shows. */
  public boolean enableAnimations = true;
  /** If true, text elements are drawn. Set to false during export of book html */
  public boolean drawText = true;

  private ArrowButton previousArrow, nextArrow, backArrow, indexArrow;

  public final BookData book;
  @Nullable
  private final Consumer<String> pageUpdater;
  @Nullable
  private final Consumer<?> bookPickup;

  private int page = -1;
  private int oldPage = -2;
  private final ArrayList<BookElement> leftElements = new ArrayList<>();
  private final ArrayList<BookElement> rightElements = new ArrayList<>();

  public AdvancementCache advancementCache;

  private double[] lastClick;
  private double[] lastDrag;

  // TODO: maybe make this a list with ability to add custom layers
  private static final ILayerRenderFunction[] LAYERS = {
    // Main layer
    BookElement::draw,
    // Overlay layer
    BookElement::drawOverlay
  };

  public BookScreen(Component title, BookData book, String page, @Nullable Consumer<String> pageUpdater, @Nullable Consumer<?> bookPickup) {
    super(Minecraft.getInstance(), Minecraft.getInstance().font, title);
    this.book = book;
    this.pageUpdater = pageUpdater;
    this.bookPickup = bookPickup;

    ClientAdvancements advancements = this.minecraft.player != null ? this.minecraft.player.connection.getAdvancements() : null;
    this.advancementCache = new AdvancementCache(advancements);
    if (advancements != null) {
      advancements.setListener(this.advancementCache);
    }
    this.openPage(book.findPageNumber(page, this.advancementCache));
  }

  /**
   * Creates a font that draws text in the default font using the given font instead.
   * Other fonts set through component styles are kept, which keeps sprite and player head text working.
   * The glyphs come from the main font, so the font stays valid after resource reloads.
   * @param font  Font to use in place of the default font
   */
  private static Font createReplacedFont(Identifier font) {
    FontDescription replacement = new FontDescription.Resource(font);
    Font.Provider base = Minecraft.getInstance().font.provider;
    return new Font(new Font.Provider() {
      @Override
      public GlyphSource glyphs(FontDescription description) {
        return base.glyphs(FontDescription.DEFAULT.equals(description) ? replacement : description);
      }

      @Override
      public EffectGlyph effect() {
        return base.effect();
      }
    });
  }

  /** Gets the alt Minecraft font */
  public static Font getAltFont() {
    if (altFont == null) {
      altFont = createReplacedFont(ALT_FONT);
    }
    return altFont;
  }

  /** Gets the uniform version of the Minecraft font */
  public static Font getUniformFont() {
    if (uniformFont == null) {
      uniformFont = createReplacedFont(UNIFORM_FONT);
    }
    return uniformFont;
  }

  public Font getFontRenderer() {
    Font fontRenderer = this.book.fontRenderer;
    if (fontRenderer == null) {
      fontRenderer = Objects.requireNonNull(this.minecraft).font;
    }

    return fontRenderer;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX ,int mouseY, float partialTicks) {
    // the (blurred) backdrop is drawn on its own layer before this method runs, so it stays behind the book

    Font fontRenderer = getFontRenderer();

    if (debug) {
      graphics.fill(0, 0, fontRenderer.width("DEBUG") + 4, fontRenderer.lineHeight + 4, 0x55000000);
      graphics.text(this.font, "DEBUG", 2, 2, 0xFFFFFFFF, false);
    }

    // book colors are RGB, so make them opaque
    int coverColor = ARGB.opaque(this.book.appearance.coverColor);

    if(this.page == -1) {
      this.renderCover(graphics, coverColor);
    } else {
      Matrix3x2fStack matrixStack = graphics.pose();

      boolean renderLeft = shouldRenderPage(this.page, false);
      boolean renderRight = shouldRenderPage(this.page, true);

      renderUnderLayer(graphics, coverColor);

      if(renderLeft) {
        renderPageBackground(graphics, false);
      }

      if(renderRight) {
        renderPageBackground(graphics, true);
      }

      // add page numbers at bottom
      if (this.book.appearance.drawPageNumbers) {
        if (renderLeft) {
          String pNum = this.page * 2 + "";
          matrixStack.pushMatrix();
          drawerTransform(matrixStack, false);
          matrixStack.translate((PAGE_WIDTH - fontRenderer.width(pNum)) / 2f, PAGE_HEIGHT - 10);
          graphics.text(fontRenderer, pNum, 0, 0, 0xFFAAAAAA, false);
          matrixStack.popMatrix();
        }
        if (renderRight) {
          String pNum = this.page * 2 + 1 + "";
          matrixStack.pushMatrix();
          drawerTransform(matrixStack, true);
          matrixStack.translate((PAGE_WIDTH - fontRenderer.width(pNum)) / 2f, PAGE_HEIGHT - 10);
          graphics.text(fontRenderer, pNum, 0, 0, 0xFFAAAAAA, false);
          matrixStack.popMatrix();
        }
      }

      int leftMX = this.getMouseX(false);
      int rightMX = this.getMouseX(true);
      int mY = this.getMouseY();

      // TODO: can we draw the left all at once then the right all at once to reduce number of matrix operations?
      // we did that in 1.16.5 - causes tooltips of left to draw under elements on right
      for (ILayerRenderFunction layer : LAYERS) {
        if(renderLeft) {
          matrixStack.pushMatrix();
          drawerTransform(matrixStack, false);
          matrixStack.scale(PAGE_SCALE, PAGE_SCALE);
          renderPageLayer(graphics, leftMX, mY, partialTicks, leftElements, layer);
          matrixStack.popMatrix();
        }

        if(renderRight) {
          matrixStack.pushMatrix();
          drawerTransform(matrixStack, true);
          matrixStack.scale(PAGE_SCALE, PAGE_SCALE);
          renderPageLayer(graphics, rightMX, mY, partialTicks, rightElements, layer);
          matrixStack.popMatrix();
        }
      }
    }

    // Render widgets (page arrows, index button)
    super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
  }

  private boolean shouldRenderPage(int pageNum, boolean rightSide) {
    if(!rightSide) {
      return pageNum != 0;
    }

    int fullPageCount = this.book.getFullPageCount(this.advancementCache);
    return this.page < fullPageCount - 1 || this.book.getPageCount(this.advancementCache) % 2 != 0;
  }

  private void renderCover(GuiGraphicsExtractor graphics, int coverColor) {
    Font fontRenderer = getFontRenderer();

    Identifier cover = book.appearance.getCoverTexture();

    int centerX = this.width / 2 - PAGE_WIDTH_UNSCALED / 2;
    int centerY = this.height / 2 - PAGE_HEIGHT_UNSCALED / 2;

    graphics.blit(RenderPipelines.GUI_TEXTURED, cover, centerX, centerY, 0, 0, PAGE_WIDTH_UNSCALED, PAGE_HEIGHT_UNSCALED, TEX_SIZE, TEX_SIZE, coverColor);

    Matrix3x2fStack matrixStack = graphics.pose();
    int textColor = ARGB.opaque(this.book.appearance.getCoverTextColor());
    if (!this.book.appearance.title.isEmpty()) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, cover, centerX, centerY, 0, PAGE_HEIGHT_UNSCALED, PAGE_WIDTH_UNSCALED, PAGE_HEIGHT_UNSCALED, TEX_SIZE, TEX_SIZE);

      matrixStack.pushMatrix();

      int width = this.font.width(this.book.appearance.title);
      float scale = Mth.clamp((float)PAGE_WIDTH / width, 0F, 2.5F);

      matrixStack.scale(scale, scale);

      graphics.text(this.font, this.book.appearance.title, (int)((this.width / 2F) / scale + 3 - width / 2F), (int)((this.height / 2F - fontRenderer.lineHeight / 2F) / scale - 4), textColor, true);
      matrixStack.popMatrix();
    }

    if (!this.book.appearance.subtitle.isEmpty()) {
      matrixStack.pushMatrix();

      int width = this.font.width(this.book.appearance.subtitle);
      float scale = Mth.clamp((float)PAGE_WIDTH / width, 0F, 1.5F);

      matrixStack.scale(scale, scale);
      graphics.text(this.font, this.book.appearance.subtitle, (int)((this.width / 2F) / scale + 7 - width / 2F), (int)((this.height / 2F + 100 - fontRenderer.lineHeight * 2) / scale), textColor, true);
      matrixStack.popMatrix();
    }
  }

  private void renderUnderLayer(GuiGraphicsExtractor graphics, int coverColor) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, this.book.appearance.getBookTexture(), this.width / 2 - PAGE_WIDTH_UNSCALED, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2, 0, 0, PAGE_WIDTH_UNSCALED * 2, PAGE_HEIGHT_UNSCALED, TEX_SIZE, TEX_SIZE, coverColor);
  }

  private void renderPageBackground(GuiGraphicsExtractor graphics, boolean rightSide) {
    int pageTint = ARGB.opaque(this.book.appearance.getPageTint());
    if(!rightSide) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, book.appearance.getBookTexture(), this.width / 2 - PAGE_WIDTH_UNSCALED, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2, 0, PAGE_HEIGHT_UNSCALED, PAGE_WIDTH_UNSCALED, PAGE_HEIGHT_UNSCALED, TEX_SIZE, TEX_SIZE, pageTint);
    } else {
      graphics.blit(RenderPipelines.GUI_TEXTURED, book.appearance.getBookTexture(), this.width / 2, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2, PAGE_WIDTH_UNSCALED, PAGE_HEIGHT_UNSCALED, PAGE_WIDTH_UNSCALED, PAGE_HEIGHT_UNSCALED, TEX_SIZE, TEX_SIZE, pageTint);
    }
  }

  private void renderPageLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, List<BookElement> elements, ILayerRenderFunction layerFunc) {
    Font font = getFontRenderer();

    for (BookElement element : elements) {
      if (!drawText && element.isText()) continue;
      layerFunc.draw(element, graphics, mouseX, mouseY, partialTicks, font);
    }
  }

  @Override
  protected void init() {
    super.init();

    clearWidgets();

    this.previousArrow = this.addRenderableWidget(new ArrowButton(book, 50, -50, ArrowButton.ArrowType.PREV, this.book.appearance.arrowColor, this.book.appearance.arrowColorHover, (p_212998_1_) -> {
      this.page--;

      if (this.page < -1) {
        this.page = -1;
      }

      this.oldPage = -2;
      this.buildPages();
    }));

    this.nextArrow = this.addRenderableWidget(new ArrowButton(book, -50, -50, ArrowButton.ArrowType.NEXT, this.book.appearance.arrowColor, this.book.appearance.arrowColorHover, (p_212998_1_) -> {
      this.page++;

      int fullPageCount = this.book.getFullPageCount(this.advancementCache);

      if (this.page >= fullPageCount) {
        this.page = fullPageCount - 1;
      }

      this.oldPage = -2;
      this.buildPages();
    }));

    this.backArrow = this.addRenderableWidget(new ArrowButton(book, this.width / 2 - ArrowButton.WIDTH / 2, this.height / 2 + ArrowButton.HEIGHT / 2 + PAGE_HEIGHT / 2, ArrowButton.ArrowType.LEFT, this.book.appearance.arrowColor, this.book.appearance.arrowColorHover, (p_212998_1_) -> {
      if (this.oldPage >= -1) {
        this.page = this.oldPage;
      }

      this.oldPage = -2;
      this.buildPages();
    }));

    this.indexArrow = this.addRenderableWidget(new ArrowButton(book, this.width / 2 - PAGE_WIDTH_UNSCALED - ArrowButton.WIDTH / 2, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2, ArrowButton.ArrowType.BACK_UP, this.book.appearance.arrowColor, this.book.appearance.arrowColorHover, (p_212998_1_) -> {
      this.openPage(this.book.findPageNumber("index.page1", this.advancementCache));

      this.oldPage = -2;
      this.buildPages();
    }));

    if(this.bookPickup != null) {
      int margin = 10;
      if(this.height / 2 + PAGE_HEIGHT_UNSCALED / 2 + margin + 20 >= this.height) {
        margin = 0;
      }

      this.addRenderableWidget(Button.builder(Component.translatable("lectern.take_book"), button -> {
        this.onClose();
        this.bookPickup.accept(null);
      }).pos(this.width / 2 - 196 / 2, this.height / 2 + PAGE_HEIGHT_UNSCALED / 2 + margin).size(196, 20).build());
    }

    this.buildPages();
  }

  @Override
  public void tick() {
    super.tick();

    this.previousArrow.visible = this.page != -1 && drawArrows;
    this.nextArrow.visible = this.page + 1 < this.book.getFullPageCount(this.advancementCache) && drawArrows;
    this.backArrow.visible = this.oldPage >= -1 && drawArrows;

    if (this.page == -1) {
      this.nextArrow.setX(this.width / 2 + 80);
      this.indexArrow.visible = false;
    } else {
      this.previousArrow.setX(this.width / 2 - 184);
      this.nextArrow.setX(this.width / 2 + 165);

      SectionData index = this.book.findSection("index", this.advancementCache);
      this.indexArrow.visible = index != null && (this.page - 1) * 2 + 2 > index.getPageCount() && drawArrows;
    }

    this.previousArrow.setY(this.height / 2 + 75);
    this.nextArrow.setY(this.height / 2 + 75);
  }

  /** Goes to the previous page */
  public boolean previousPage() {
    this.page--;
    if (this.page < -1) {
      this.page = -1;

      return false;
    }
    this.oldPage = -2;
    this.buildPages();

    return true;
  }

  /** Goes to the next page */
  public boolean nextPage() {
    this.page++;
    int fullPageCount = this.book.getFullPageCount(this.advancementCache);
    if (this.page >= fullPageCount) {
      this.page = fullPageCount - 1;

      return false;
    }
    this.oldPage = -2;
    this.buildPages();

    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    boolean handled = super.keyPressed(event);

    switch (event.key()) {
      case InputConstants.KEY_LEFT, InputConstants.KEY_A -> {
        previousPage();
        return true;
      }
      case InputConstants.KEY_RIGHT, InputConstants.KEY_D -> {
        nextPage();
        return true;
      }
      case InputConstants.KEY_F3 -> {
        debug = !debug;
        return true;
      }
    }

    return handled;
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
    if (scrollDelta < 0.0D) {
      nextPage();
      return true;
    } else if (scrollDelta > 0.0D) {
      previousPage();
      return true;
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
  }

  /**
   * Converts a mouse button from the input events to the button numbers used by {@link BookElement}.
   * @param button  Button from {@link MouseButtonEvent#button()}, left is {@link InputConstants#MOUSE_BUTTON_LEFT}
   * @return  Legacy button number: 0 is left, 1 is right, 2 is middle, further buttons count up from 3
   */
  public static int legacyMouseButton(int button) {
    return switch (button) {
      case InputConstants.MOUSE_BUTTON_LEFT -> 0;
      case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
      case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
      default -> button - 1;
    };
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    boolean right = false;

    double mouseX = this.getMouseX(false);
    double mouseY = this.getMouseY();
    int mouseButton = legacyMouseButton(event.button());

    if (mouseX > PAGE_WIDTH + (PAGE_MARGIN + PAGE_PADDING_LEFT) / PAGE_SCALE) {
      mouseX = this.getMouseX(true);
      right = true;
    }

    lastClick = new double[]{mouseX, mouseY};

    // Not foreach to prevent conmodification crashes
    int oldPage = this.page;
    List<BookElement> elementList = List.copyOf(right ? this.rightElements : this.leftElements);
    for (BookElement element : elementList) {
      element.mouseClicked(mouseX, mouseY, mouseButton);
      // if we changed page stop so we don't act on the new page
      if (this.page != oldPage) {
        return true;
      }
    }

    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    boolean right = false;
    double mouseX = this.getMouseX(false);
    double mouseY = this.getMouseY();
    int mouseButton = legacyMouseButton(event.button());

    if (mouseX > PAGE_WIDTH + (PAGE_MARGIN + PAGE_PADDING_LEFT) / PAGE_SCALE) {
      mouseX = this.getMouseX(true);
      right = true;
    }

    // Not foreach to prevent conmodification crashes
    for (int i = 0; right ? i < this.rightElements.size() : i < this.leftElements.size(); i++) {
      BookElement element = right ? this.rightElements.get(i) : this.leftElements.get(i);
      element.mouseReleased(mouseX, mouseY, mouseButton);
    }

    lastClick = null;
    lastDrag = null;

    return super.mouseReleased(event);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    boolean right = false;
    double mouseX = this.getMouseX(false);
    double mouseY = this.getMouseY();
    int button = legacyMouseButton(event.button());

    if (mouseX > PAGE_WIDTH + (PAGE_MARGIN + PAGE_PADDING_LEFT) / PAGE_SCALE) {
      mouseX = this.getMouseX(true);
      right = true;
    }

    if (lastClick != null) {
      if (lastDrag == null)
        lastDrag = new double[]{mouseX, mouseY};

      // Not foreach to prevent conmodification crashes
      for (int i = 0; right ? i < this.rightElements.size() : i < this.leftElements.size(); i++) {
        BookElement element = right ? this.rightElements.get(i) : this.leftElements.get(i);
        element.mouseDragged(lastClick[0], lastClick[1], mouseX, mouseY, lastDrag[0], lastDrag[1], button);
      }

      lastDrag = new double[]{mouseX, mouseY};
    }


    return true;
  }

  @Override
  public void removed() {
    if (this.minecraft == null || this.minecraft.player == null) {
      return;
    }
    // find what page to update
    if (pageUpdater != null) {
      String pageStr = "";
      if (this.page >= 0) {
        PageData page = this.page == 0 ? this.book.findPage(0, this.advancementCache) : getLeftPage();
        if (page == null) {
          page = getRightPage();
        }
        if (page != null && page.parent != null) {
          pageStr = page.parent.name + "." + page.name;
        }
      }
      pageUpdater.accept(pageStr);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  public void drawerTransform(Matrix3x2fStack matrixStack, boolean rightSide) {
    if (rightSide) {
      matrixStack.translate(this.width / 2 + PAGE_PADDING_RIGHT + PAGE_MARGIN, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2 + PAGE_PADDING_TOP + PAGE_MARGIN);
    } else {
      matrixStack.translate(this.width / 2 - PAGE_WIDTH_UNSCALED + PAGE_PADDING_LEFT + PAGE_MARGIN, this.height / 2 - PAGE_HEIGHT_UNSCALED / 2 + PAGE_PADDING_TOP + PAGE_MARGIN);
    }
  }

  // offset to the left edge of the left/right side
  protected float leftOffset(boolean rightSide) {
    if (rightSide) {
      // from center: go padding + margin to the right
      return this.width / 2 + PAGE_PADDING_RIGHT + PAGE_MARGIN;
    } else {
      // from center: go page width left, then right with padding and margin
      return this.width / 2 - PAGE_WIDTH_UNSCALED + PAGE_PADDING_LEFT + PAGE_MARGIN;
    }
  }

  protected float topOffset() {
    return this.height / 2 - PAGE_HEIGHT_UNSCALED / 2 + PAGE_PADDING_TOP + PAGE_MARGIN;
  }

  protected int getMouseX(boolean rightSide) {
    assert this.minecraft != null;
    if(!mouseInput) {
      return -1;
    }

    return (int) ((Minecraft.getInstance().mouseHandler.xpos() * this.width / this.minecraft.getWindow().getScreenWidth() - this.leftOffset(rightSide)) / PAGE_SCALE);
  }

  protected int getMouseY() {
    assert this.minecraft != null;
    if(!mouseInput) {
      return -1;
    }

    return (int) ((Minecraft.getInstance().mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight() - 1 - this.topOffset()) / PAGE_SCALE);
  }

  public int openPage(int page) {
    return this.openPage(page, false);
  }

  public int openPage(int page, boolean returner) {
    if (page < 0) {
      return -1;
    }

    int bookPage;
    if (page == 1) {
      bookPage = 0;
    } else if (page % 2 == 0) {
      bookPage = (page - 1) / 2 + 1;
    } else {
      bookPage = (page - 2) / 2 + 1;
    }

    if (bookPage >= -1 && bookPage < this.book.getFullPageCount(this.advancementCache)) {
      if (returner) {
        this.oldPage = this.page;
      }

      this._setPage(bookPage);
    }

    return page % 2 == 0 ? 0 : 1;
  }

  public void _setPage(int page) {
    this.page = page;
    this.buildPages();
  }

  public int getPage(int side) {
    if (this.page == 0 && side == 0) {
      return -1;
    } else if (this.page == 0 && side == 1) {
      return 0;
    } else if (side == 0) {
      return (this.page - 1) * 2 + 1;
    } else if (side == 1) {
      return (this.page - 2) * 2 + 2;
    } else {
      return -1;
    }
  }

  public int getPage_() {
    return this.page;
  }

  public List<BookElement> getElements(int side) {
    return side == 0 ? this.leftElements : side == 1 ? this.rightElements : Collections.emptyList();
  }

  public void openCover() {
    this._setPage(-1);

    this.leftElements.clear();
    this.rightElements.clear();
    this.buildPages();
  }

  private void buildPages() {
    this.leftElements.clear();
    this.rightElements.clear();

    if (this.page == -1) {
      return;
    }

    if (this.page == 0) {
      PageData page = this.book.findPage(0, this.advancementCache);

      if (page != null) {
        page.content.build(this.book, this.rightElements, false);
      }
    } else {
      PageData leftPage = getLeftPage();
      PageData rightPage = getRightPage();

      if (leftPage != null) {
        leftPage.content.build(this.book, this.leftElements, false);
      }
      if (rightPage != null) {
        rightPage.content.build(this.book, this.rightElements, true);
      }
    }

    for (BookElement element : this.leftElements) {
      element.setParent(this);
    }
    for (BookElement element : this.rightElements) {
      element.setParent(this);
    }
  }

  /** {@return PageData from the left page } */
  @Nullable
  private PageData getLeftPage() {
    return this.book.findPage((this.page - 1) * 2 + 1, this.advancementCache);
  }

  /** {@return PageData from the right page } */
  @Nullable
  private PageData getRightPage() {
    return this.book.findPage((this.page - 1) * 2 + 2, this.advancementCache);
  }


  /**
   * Converts the cover to HTML.
   * Preconditon: {@link #getPage_()} is -1.
   */
  public String coverToHtml(String bookName, String title, String version, String mod) {
    return "---\n" +
      "layout: book-cover\n" +
      "title: " + title + " (" + version + ")\n" +
      "breadcrumb: " + title + "\n" +
      "description: Interactive " + title + " from " + mod + " in Minecraft " + version + ".\n" +
      "book: " + bookName + "\n" +
      "---\n\n";
  }

  /**
   * Converts the current left and right page to HTML including the Jekyll front matter.
   * Preconditon: {@link #getPage_()} is not -1.
   */
  public String pageToHtml(String bookName, String title, String version, String mod) {
    PageData leftData = getLeftPage();
    PageData rightData = getRightPage();

    StringBuilder builder = new StringBuilder();

    // create page if we have data on either side
    if (leftData != null || rightData != null) {
      HtmlSerializable left = leftData != null ? leftData.content.toHTML(book) : null;
      HtmlSerializable right = rightData != null ? rightData.content.toHTML(book) : null;

      builder.append("---\n")
        .append("layout: book-page\n")
        .append("title: ").append(title).append(" (").append(version).append(") - page ").append(this.page).append('\n')
        .append("breadcrumb: ").append(this.page).append('\n')
        .append("description: Interactive ").append(title).append(" from ").append(mod).append(" in Minecraft ").append(version);

      // add page titles to description
      // working off the assumption that any page with content to export has a name to export
      String leftTitle = left != null ? leftData.getTitle() : "";
      String rightTitle = right != null ? rightData.getTitle() : "";
      // if we have either title, add the "showing" text
      if (!leftTitle.isEmpty() || !rightTitle.isEmpty()) {
        builder.append(" showing ");
        builder.append(leftTitle);
        // if we have both, separate them with "and"
        if (!leftTitle.isEmpty() && !rightTitle.isEmpty()) {
          builder.append(" and ");
        }
        builder.append(rightTitle);
      }

      builder.append(".\n")
        .append("book: ").append(bookName).append('\n')
        .append("page_num: ").append(this.page).append('\n');

      // figure out what section we are in
      // prioritize left as it may not be in the same section as right
      SectionData section = Objects.requireNonNullElse(leftData, rightData).parent;
      if (section != null) {
        int sectionStart = section.parent.getFirstPageNumber(section, null) / 2;
        // if it starts before the current page, store it as the section start
        if (sectionStart < this.page) {
          builder.append("section_start: ").append(sectionStart).append('\n');
        }
      }

      builder.append("---\n\n");

      // add page data
      if (left != null) {
        HtmlElement.div().classes("left").add(left).toHtml(builder, "");
        builder.append('\n');
      }
      if (right != null) {
        HtmlElement.div().classes("right").add(right).toHtml(builder, "");
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  /** Cache of advancement progress for the book, kept in sync by registering as the advancement listener */
  public static class AdvancementCache implements ClientAdvancements.Listener {

    private final HashMap<Advancement, AdvancementProgress> progress = new HashMap<>();
    private final HashMap<Identifier, Advancement> nameCache = new HashMap<>();
    /** Advancements to read from, if null uses the advancements of the current connection */
    @Nullable
    private final ClientAdvancements advancements;

    public AdvancementCache(@Nullable ClientAdvancements advancements) {
      this.advancements = advancements;
    }

    public AdvancementCache() {
      this(null);
    }

    @Nullable
    public AdvancementProgress getProgress(String id) {
      return this.getProgress(this.getAdvancement(id));
    }

    @Nullable
    public AdvancementProgress getProgress(Advancement advancement) {
      return this.progress.get(advancement);
    }

    public Advancement getAdvancement(String id) {
      return this.nameCache.get(Identifier.parse(id));
    }

    /** Gets the advancements to read progress from */
    @Nullable
    private ClientAdvancements getAdvancements() {
      if (this.advancements != null) {
        return this.advancements;
      }
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      return connection != null ? connection.getAdvancements() : null;
    }

    @Override
    public void onAdvancementsUpdated() {
      // listener no longer notifies per advancement, so rebuild the cache from the full state
      this.progress.clear();
      this.nameCache.clear();
      ClientAdvancements advancements = getAdvancements();
      if (advancements != null) {
        for (AdvancementNode node : advancements.tree().nodes()) {
          this.nameCache.put(node.holder().id(), node.holder().value());
        }
        for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : advancements.progress().entrySet()) {
          this.progress.put(entry.getKey().value(), entry.getValue());
        }
      }
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder advancement) {
      // noop
    }

    @Override
    public void onAdvancementsCleared() {
      this.progress.clear();
      this.nameCache.clear();
    }
  }
}
