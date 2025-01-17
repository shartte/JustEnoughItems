package mezz.jei.util;

import mezz.jei.config.IClientConfig;
import mezz.jei.config.ServerInfo;
import mezz.jei.network.Network;
import mezz.jei.network.packets.PacketGiveItemStack;
import mezz.jei.network.packets.PacketSetHotbarItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.inventory.CreativeScreen;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.client.util.InputMappings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CommandUtil {
	private static final Logger LOGGER = LogManager.getLogger();

	private CommandUtil() {
	}

	/**
	 * /give &lt;player&gt; &lt;item&gt; [amount]
	 * <p>
	 * {@link CreativeScreen} has special client-side handling for itemStacks, just give the item on the client
	 */
	public static void giveStack(ItemStack itemStack, InputMappings.Input input, IClientConfig clientConfig) {
		final GiveMode giveMode = clientConfig.getGiveMode();
		Minecraft minecraft = Minecraft.getInstance();
		ClientPlayerEntity player = minecraft.player;
		if (player == null) {
			LOGGER.error("Can't give stack, there is no player");
			return;
		}
		if (minecraft.screen instanceof CreativeScreen && giveMode == GiveMode.MOUSE_PICKUP) {
			final int amount = GiveMode.getStackSize(giveMode, itemStack, input);
			ItemStack sendStack = ItemHandlerHelper.copyStackWithSize(itemStack, amount);
			CommandUtilServer.mousePickupItemStack(player, sendStack);
		} else if (ServerInfo.isJeiOnServer()) {
			final int amount = GiveMode.getStackSize(giveMode, itemStack, input);
			ItemStack sendStack = ItemHandlerHelper.copyStackWithSize(itemStack, amount);
			PacketGiveItemStack packet = new PacketGiveItemStack(sendStack, giveMode);
			Network.sendPacketToServer(packet);
		} else {
			int amount = GiveMode.getStackSize(GiveMode.INVENTORY, itemStack, input);
			giveStackVanilla(itemStack, amount);
		}
	}

	public static void setHotbarStack(ItemStack itemStack, int hotbarSlot) {
		if (ServerInfo.isJeiOnServer()) {
			ItemStack sendStack = ItemHandlerHelper.copyStackWithSize(itemStack, itemStack.getMaxStackSize());
			PacketSetHotbarItemStack packet = new PacketSetHotbarItemStack(sendStack, hotbarSlot);
			Network.sendPacketToServer(packet);
		}
	}

	/**
	 * Fallback for when JEI is not on the server, tries to use the /give command
	 * Uses the Creative Inventory Action Packet when in creative, which doesn't require the player to be op.
	 */
	private static void giveStackVanilla(ItemStack itemStack, int amount) {
		if (itemStack.isEmpty()) {
			String stackInfo = ErrorUtil.getItemStackInfo(itemStack);
			LOGGER.error("Empty itemStack: {}", stackInfo, new IllegalArgumentException());
			return;
		}

		Item item = itemStack.getItem();
		ResourceLocation itemResourceLocation = item.getRegistryName();
		ErrorUtil.checkNotNull(itemResourceLocation, "itemStack.getItem().getRegistryName()");

		ClientPlayerEntity sender = Minecraft.getInstance().player;
		if (sender != null) {
			if (sender.createCommandSourceStack().hasPermission(2)) {
				sendGiveAction(sender, itemStack, amount);
			} else if (sender.isCreative()) {
				sendCreativeInventoryActions(sender, itemStack, amount);
			} else {
				// try this in case the vanilla server has permissions set so regular players can use /give
				sendGiveAction(sender, itemStack, amount);
			}
		}
	}

	private static void sendGiveAction(ClientPlayerEntity sender, ItemStack itemStack, int amount) {
		String[] commandParameters = CommandUtilServer.getGiveCommandParameters(sender, itemStack, amount);
		String fullCommand = "/give " + StringUtils.join(commandParameters, " ");
		sendChatMessage(sender, fullCommand);
	}

	private static void sendChatMessage(ClientPlayerEntity sender, String chatMessage) {
		if (chatMessage.length() <= 256) {
			sender.chat(chatMessage);
		} else {
			ITextComponent errorMessage = new TranslationTextComponent("jei.chat.error.command.too.long");
			errorMessage.getStyle().applyFormat(TextFormatting.RED);
			sender.displayClientMessage(errorMessage, false);

			ITextComponent chatMessageComponent = new StringTextComponent(chatMessage);
			chatMessageComponent.getStyle().applyFormat(TextFormatting.RED);
			sender.displayClientMessage(chatMessageComponent, false);
		}
	}

	private static void sendCreativeInventoryActions(ClientPlayerEntity sender, ItemStack stack, int amount) {
		int i = 0; // starting in the inventory, not armour or crafting slots
		while (i < sender.inventory.items.size() && amount > 0) {
			ItemStack currentStack = sender.inventory.items.get(i);
			if (currentStack.isEmpty()) {
				ItemStack sendAllRemaining = ItemHandlerHelper.copyStackWithSize(stack, amount);
				sendSlotPacket(sendAllRemaining, i);
				amount = 0;
			} else if (currentStack.sameItem(stack) && currentStack.getMaxStackSize() > currentStack.getCount()) {
				int canAdd = Math.min(currentStack.getMaxStackSize() - currentStack.getCount(), amount);
				ItemStack fillRemainingSpace = ItemHandlerHelper.copyStackWithSize(stack, canAdd + currentStack.getCount());
				sendSlotPacket(fillRemainingSpace, i);
				amount -= canAdd;
			}
			i++;
		}
		if (amount > 0) {
			ItemStack toDrop = ItemHandlerHelper.copyStackWithSize(stack, amount);
			sendSlotPacket(toDrop, -1);
		}
	}

	private static void sendSlotPacket(ItemStack stack, int mainInventorySlot) {
		if (mainInventorySlot < 9 && mainInventorySlot != -1) {
			// slot ID for the message is different from the slot id used in the mainInventory
			mainInventorySlot += 36;
		}
		Minecraft minecraft = Minecraft.getInstance();
		PlayerController playerController = minecraft.gameMode;
		if (playerController != null) {
			playerController.handleCreativeModeItemAdd(stack, mainInventorySlot);
		} else {
			LOGGER.error("Cannot send slot packet, minecraft.playerController is null");
		}
	}
}
