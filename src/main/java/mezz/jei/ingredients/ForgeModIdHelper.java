package mezz.jei.ingredients;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.config.IClientConfig;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.config.ModIdFormattingConfig;
import org.apache.commons.lang3.StringUtils;

public class ForgeModIdHelper extends AbstractModIdHelper {
	private final IClientConfig config;
	private final ModIdFormattingConfig modIdFormattingConfig;

	public ForgeModIdHelper(IClientConfig config, ModIdFormattingConfig modIdFormattingConfig) {
		this.config = config;
		this.modIdFormattingConfig = modIdFormattingConfig;
	}

	@Override
	public String getModNameForModId(String modId) {
		return ModList.get().getModContainerById(modId)
			.map(modContainer -> modContainer.getModInfo().getDisplayName())
			.orElse(StringUtils.capitalize(modId));
	}

	@Override
	public boolean isDisplayingModNameEnabled() {
		String modNameFormat = modIdFormattingConfig.getModNameFormat();
		return !modNameFormat.isEmpty();
	}

	@Override
	public String getFormattedModNameForModId(String modId) {
		String modName = getModNameForModId(modId);
		modName = removeChatFormatting(modName); // some crazy mod has formatting in the name
		String modNameFormat = modIdFormattingConfig.getModNameFormat();
		if (!modNameFormat.isEmpty()) {
			if (modNameFormat.contains(ModIdFormattingConfig.MOD_NAME_FORMAT_CODE)) {
				return StringUtils.replaceOnce(modNameFormat, ModIdFormattingConfig.MOD_NAME_FORMAT_CODE, modName);
			}
			return modNameFormat + modName;
		}
		return modName;
	}

	private static String removeChatFormatting(String string) {
		String withoutFormattingCodes = TextFormatting.stripFormatting(string);
		return (withoutFormattingCodes == null) ? "" : withoutFormattingCodes;
	}

	@Override
	public <T> List<ITextComponent> addModNameToIngredientTooltip(List<ITextComponent> tooltip, T ingredient, IIngredientHelper<T> ingredientHelper) {
		if (config.isDebugModeEnabled() && Minecraft.getInstance().options.advancedItemTooltips) {
			tooltip = addDebugInfo(tooltip, ingredient, ingredientHelper);
		}
		if (modIdFormattingConfig.isModNameFormatOverrideActive() && (ingredient instanceof ItemStack)) {
			// we detected that another mod is adding the mod name already
			return tooltip;
		}
		String modNameFormat = modIdFormattingConfig.getModNameFormat();
		if (modNameFormat.isEmpty()) {
			return tooltip;
		}
		return super.addModNameToIngredientTooltip(tooltip, ingredient, ingredientHelper);
	}

	private <T> List<ITextComponent> addDebugInfo(List<ITextComponent> tooltip, T ingredient, IIngredientHelper<T> ingredientHelper) {
		tooltip = new ArrayList<>(tooltip);
		StringTextComponent jeiDebug = new StringTextComponent("JEI Debug:");
		StringTextComponent info = new StringTextComponent("info: " + ingredientHelper.getErrorInfo(ingredient));
		StringTextComponent uid = new StringTextComponent("uid: " + ingredientHelper.getUniqueId(ingredient, UidContext.Ingredient));
		tooltip.add(jeiDebug.withStyle(TextFormatting.GRAY));
		tooltip.add(info.withStyle(TextFormatting.GRAY));
		tooltip.add(uid.withStyle(TextFormatting.GRAY));
		return tooltip;
	}
}
