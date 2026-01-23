package slimeknights.mantle.client.book.data.element;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

@Accessors(fluent = true)
@Setter
public class TextData {
  /** @deprecated use {@link #linebreak} */
  @Deprecated(forRemoval = true)
  public static final TextData LINEBREAK = new TextData().linebreak(true);

  // TODO 1.21: make no longer nullable
  @Nullable
  public String text = "";
  public String color = "black";

  public int rgbColor = 0;
  public boolean useOldColor = true;
  public boolean bold = false;
  public boolean italic = false;
  public boolean underlined = false;
  public boolean strikethrough = false;
  public boolean obfuscated = false;
  /** Adds 2 linebreaks before the text */
  public boolean paragraph = false;
  /** If true, adds a line break after the text */
  public boolean linebreak = false;
  public boolean dropshadow = false;
  public float scale = 1;
  public String action = "";
  @Nullable
  public Component[] tooltip = null;

  public TextData(String text) {
    this.text = text;
  }

  public TextData() {
    this("");
  }

  /** Null safe method to get text, as its possible its null due to book parsing. */
  public String getText() {
    return text == null ? "" : text;
  }
}
