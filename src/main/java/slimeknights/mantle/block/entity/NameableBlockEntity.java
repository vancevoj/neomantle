package slimeknights.mantle.block.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Extension of tile entity to make it namable
 */
public abstract class NameableBlockEntity extends MantleBlockEntity implements INameableMenuProvider {
	private static final String TAG_CUSTOM_NAME = "CustomName";

	/** Default title for this tile entity */
	@Getter
	private final Component defaultName;
	/** Title set to this tile entity */
	@Getter @Setter
	private Component customName;

	public NameableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Component defaultTitle) {
		super(type, pos, state);
		this.defaultName = defaultTitle;
	}

	/**
	 * Reads the custom name from the given input. Worlds saved before 1.21.6 stored the name as a JSON string, which the component codec would read as literal text.
	 * @return  Custom name, or null if not present
	 */
	@Nullable
	private static Component readCustomName(ValueInput input) {
		Optional<String> string = input.getString(TAG_CUSTOM_NAME);
		if (string.isPresent()) {
			String json = string.get();
			if (json.startsWith("{") || json.startsWith("\"")) {
				try {
					JsonElement element = JsonParser.parseString(json);
					Optional<Component> parsed = ComponentSerialization.CODEC.parse(input.lookup().createSerializationContext(JsonOps.INSTANCE), element).result();
					if (parsed.isPresent()) {
						return parsed.get();
					}
				} catch (JsonParseException e) {
					// not legacy JSON, so it is a literal name from the component codec
				}
			}
		}
		return parseCustomNameSafe(input, TAG_CUSTOM_NAME);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Component name = readCustomName(input);
		if (name != null) {
			this.customName = name;
		}
	}

	@Override
	public void saveSynced(ValueOutput output) {
		super.saveSynced(output);
		if (this.hasCustomName()) {
			output.storeNullable(TAG_CUSTOM_NAME, ComponentSerialization.CODEC, this.customName);
		}
	}
}
