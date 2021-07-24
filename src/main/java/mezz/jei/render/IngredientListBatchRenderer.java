package mezz.jei.render;

import com.mojang.blaze3d.vertex.PoseStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.entity.ItemRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import com.google.common.base.Preconditions;
import mezz.jei.api.ingredients.ISlowRenderItem;
import mezz.jei.config.IEditModeConfig;
import mezz.jei.config.IWorldConfig;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.util.ErrorUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public class IngredientListBatchRenderer {
	private static final Logger LOGGER = LogManager.getLogger();

	private final List<IngredientListSlot> slots = new ArrayList<>();

	private final List<ItemStackFastRenderer> renderItems2d = new ArrayList<>();
	private final List<ItemStackFastRenderer> renderItems3d = new ArrayList<>();
	private final List<IngredientListElementRenderer<?>> renderOther = new ArrayList<>();
	private final IEditModeConfig editModeConfig;
	private final IWorldConfig worldConfig;

	private int blocked = 0;

	public IngredientListBatchRenderer(IEditModeConfig editModeConfig, IWorldConfig worldConfig) {
		this.editModeConfig = editModeConfig;
		this.worldConfig = worldConfig;
	}

	public void clear() {
		slots.clear();

		renderItems2d.clear();
		renderItems3d.clear();
		renderOther.clear();
		blocked = 0;
	}

	public int size() {
		return slots.size() - blocked;
	}

	public void add(IngredientListSlot ingredientListSlot) {
		slots.add(ingredientListSlot);
	}

	public List<IngredientListSlot> getAllGuiIngredientSlots() {
		return slots;
	}

	public void set(final int startIndex, List<IIngredientListElement<?>> ingredientList) {
		renderItems2d.clear();
		renderItems3d.clear();
		renderOther.clear();
		blocked = 0;

		int i = startIndex;
		for (IngredientListSlot ingredientListSlot : slots) {
			if (ingredientListSlot.isBlocked()) {
				ingredientListSlot.clear();
				blocked++;
			} else {
				if (i >= ingredientList.size()) {
					ingredientListSlot.clear();
				} else {
					IIngredientListElement<?> element = ingredientList.get(i);
					set(ingredientListSlot, element);
				}
				i++;
			}
		}
	}

	private <V> void set(IngredientListSlot ingredientListSlot, IIngredientListElement<V> element) {
		ingredientListSlot.clear();

		V ingredient = element.getIngredient();
		if (ingredient instanceof ItemStack) {
			//noinspection unchecked
			IIngredientListElement<ItemStack> itemStackElement = (IIngredientListElement<ItemStack>) element;
			ItemStack itemStack = itemStackElement.getIngredient();
			BakedModel bakedModel;
			ItemModelShaper itemModelMesher = Minecraft.getInstance().getItemRenderer().getItemModelShaper();
			try {
				bakedModel = itemModelMesher.getItemModel(itemStack);
				//TODO - 1.17: Validate
				bakedModel = bakedModel.getOverrides().resolve(bakedModel, itemStack, null, null, 0);
				Preconditions.checkNotNull(bakedModel, "IBakedModel must not be null.");
			} catch (Throwable throwable) {
				String stackInfo = ErrorUtil.getItemStackInfo(itemStack);
				LOGGER.error("ItemStack crashed getting IBakedModel. {}", stackInfo, throwable);
				return;
			}

			if (!bakedModel.isCustomRenderer() && !(itemStack.getItem() instanceof ISlowRenderItem)) {
				ItemStackFastRenderer renderer = new ItemStackFastRenderer(itemStackElement);
				ingredientListSlot.setIngredientRenderer(renderer);
				if (bakedModel.usesBlockLight()) { //isSideLit
					renderItems3d.add(renderer);
				} else {
					renderItems2d.add(renderer);
				}
				return;
			}
		}

		IngredientListElementRenderer<V> renderer = new IngredientListElementRenderer<>(element);
		ingredientListSlot.setIngredientRenderer(renderer);
		renderOther.add(renderer);
	}

	@Nullable
	public ClickedIngredient<?> getIngredientUnderMouse(double mouseX, double mouseY) {
		IngredientListElementRenderer<?> hovered = getHovered(mouseX, mouseY);
		if (hovered != null) {
			IIngredientListElement<?> element = hovered.getElement();
			return ClickedIngredient.create(element.getIngredient(), hovered.getArea());
		}
		return null;
	}

	@Nullable
	public IngredientListElementRenderer<?> getHovered(double mouseX, double mouseY) {
		for (IngredientListSlot slot : slots) {
			if (slot.isMouseOver(mouseX, mouseY)) {
				return slot.getIngredientRenderer();
			}
		}
		return null;
	}

	/**
	 * renders all ItemStacks
	 */
	@SuppressWarnings("deprecation")
	public void render(Minecraft minecraft, PoseStack poseStack) {
		//TODO - 1.17: Replacement?
		//Lighting.turnBackOn();

		ItemRenderer itemRenderer = minecraft.getItemRenderer();
		TextureManager textureManager = minecraft.getTextureManager();
		itemRenderer.blitOffset += 50.0F;

		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

		textureManager.bindForSetup(InventoryMenu.BLOCK_ATLAS);
		textureManager.getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);
		//TODO - 1.17: Replacement?
		//RenderSystem.enableRescaleNormal();
		//RenderSystem.enableAlphaTest();
		//RenderSystem.alphaFunc(GL11.GL_GREATER, 0.1F);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		// 3d Items
		//TODO - 1.17: Replacement?
		//RenderSystem.enableLighting();
		for (ItemStackFastRenderer slot : renderItems3d) {
			slot.renderItemAndEffectIntoGUI(buffer, poseStack, editModeConfig, worldConfig);
		}
		buffer.endBatch();

		// 2d Items
		//TODO - 1.17: Replacement?
		//RenderSystem.disableLighting();
		Lighting.setupForFlatItems();
		for (ItemStackFastRenderer slot : renderItems2d) {
			slot.renderItemAndEffectIntoGUI(buffer, poseStack, editModeConfig, worldConfig);
		}
		buffer.endBatch();
		Lighting.setupFor3DItems();

		//TODO - 1.17: Replacement?
		//RenderSystem.disableAlphaTest();
		RenderSystem.disableBlend();
		//TODO - 1.17: Replacement?
		//RenderSystem.disableRescaleNormal();
		//RenderSystem.disableLighting();

		textureManager.bindForSetup(InventoryMenu.BLOCK_ATLAS);
		textureManager.getTexture(InventoryMenu.BLOCK_ATLAS).restoreLastBlurMipmap();

		itemRenderer.blitOffset -= 50.0F;

		// overlays
		for (ItemStackFastRenderer slot : renderItems3d) {
			slot.renderOverlay();
		}

		for (ItemStackFastRenderer slot : renderItems2d) {
			slot.renderOverlay();
		}

		//TODO - 1.17: Replacement?
		//RenderSystem.disableLighting();

		// other rendering
		for (IngredientListElementRenderer<?> slot : renderOther) {
			slot.renderSlow(poseStack, editModeConfig, worldConfig);
		}

		//TODO - 1.17: Replacement?
		//Lighting.turnOff();
	}
}
