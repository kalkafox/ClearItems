package io.kalka.clearitems;

import net.minecraft.command.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Mod(
        modid = ClearItems.MOD_ID,
        name = ClearItems.MOD_NAME,
        version = ClearItems.VERSION,
        serverSideOnly = true,
        acceptableRemoteVersions = "*"
)
public class ClearItems {

    public static final String MOD_ID = "clearitems";
    public static final String MOD_NAME = "ClearItems";
    public static final String VERSION = "0.0.1";

    private static final Logger LOGGER = Logger.getLogger(MOD_ID);

    private static long time = 0;
    private static int skipped = 0;
    private static int items = 0;

    private static int seconds = 10;

    @Mod.Instance(MOD_ID)
    public static ClearItems INSTANCE;

    private static final WeakHashMap<EntityPlayerMP, Vector<EntityItem>> droppedItems = new WeakHashMap<>();

    public ClearItems() {
        // For those who are going to ask why this is in one file:
        // There is absolutely no need to go around splitting things apart into useless, unrelated files.
        // Everything is in one file, and it's just a matter of organization. (I'm still pretty bad at that.)
        // Object-oriented programming goes a long way for me, and in this case, keeping everything functional
        // is efficient and easy to read.
        // It is based on interpretation of course, I'm not going to tell you your way of writing sucks.
        // I'd rather just keep things functional. OOP will be a consideration only when it is actually needed.
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        // prime the time counter for the first tick (oops, was that supposed to rhyme?)
        time = System.currentTimeMillis() / 1000;
        time += 5;
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        // i herd u liek functional programming
        CommandBase clearItemsCommand = new CommandBase() {
            @Override
            public String getName() {
                return "clearitems";
            }

            @Override
            public String getUsage(ICommandSender sender) {
                return "commands.uwu.usage";
            }

            @Override
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                // check if first argument is a number
                if (args.length > 0) {
                    try {
                        int amount = Integer.parseInt(args[0]);
                        if (amount > 0) {
                            seconds = amount;
                            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + String.format("The ClearItems timer has been set to " + amount + " second%s.", amount == 1 ? "" : "s")));
                        } else {
                            throw new CommandException("The number cannot be negative or 0.");
                        }
                    } catch (NumberFormatException e) {
                        throw new CommandException("The first argument is not a number.");
                    }
                } else {
                    sender.sendMessage(new TextComponentString("Usage: /clearitems <seconds>"));
                }
            }
        };


        event.registerServerCommand(clearItemsCommand);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        long currentTime = System.currentTimeMillis();
        long timeLeft = time - currentTime / 1000;
        if (timeLeft < 0) {
            // run exponentially every second
            switch (seconds) {
                case 400:
                case 300:
                case 200:
                case 120:
                case 60:
                case 30:
                case 10:
                case 5:
                case 4:
                case 3:
                case 2:
                case 1:
                    TextFormatting color = TextFormatting.AQUA;
                    if (seconds <= 5) {
                        color = TextFormatting.YELLOW;
                    }
                    if (seconds <= 3) {
                        color = TextFormatting.RED;
                    }
                    String timeSuffix = seconds > 60 ? " minute" : " second";
                    String msg = String.format("%sHeads up! Clearing items in %s%s%s!", color, seconds > 60 ? seconds / 60 : seconds, timeSuffix, seconds > 1 ? "s" : "");
                    for (EntityPlayer player : event.world.playerEntities) {
                        player.sendStatusMessage(new TextComponentString(msg), true);
                    }
                    break;
            }
            if (seconds <= 0) {
                for (EntityPlayerMP player : droppedItems.keySet()) {
                    if (droppedItems.containsKey(player)) {
                        Vector<EntityItem> items = droppedItems.get(player);
                        // remove the item from our cache if it's not in the world anymore
                        items.removeIf(item -> item.isDead);
                    }
                }

                for (EntityPlayerMP player : droppedItems.keySet()) {
                    if (droppedItems.containsKey(player)) {
                        Vector<EntityItem> items = droppedItems.get(player);
                        // teleport the items to the player's location
                        AtomicInteger count = new AtomicInteger();
                        items.forEach(item -> {
                            if (player.hasDisconnected()) {
                                item.setDead();
                                droppedItems.get(player).remove(item);
                            } else {
                                BlockPos itemPos = item.getPosition();
                                WorldServer world = (WorldServer) event.world;
                                ChunkProviderServer chunkServer = world.getChunkProvider();
                                Chunk chunk = world.getChunk(itemPos);
                                try {
                                    chunkServer.chunkLoader.loadChunk(world, chunk.x, chunk.z);
                                    item.setPickupDelay(60);
                                    item.setPosition(player.posX, player.posY, player.posZ);
                                    count.getAndIncrement();
                                } catch (IOException e) {
                                    System.out.println("Failed to load chunk for item teleport.");
                                    e.printStackTrace();
                                }
                            }
                        });
                        if (player.hasDisconnected() && count.get() > 0) {
                            droppedItems.remove(player);
                        } else {
                            String msg = String.format("The server teleported %s%s item%s %sregistered to you.", TextFormatting.GREEN, count.get(), count.get() == 1 ? "" : "s", TextFormatting.RESET);
                            player.sendMessage(new TextComponentString(msg));
                        }
                    }
                }

                event.world.loadedEntityList.forEach(entity -> {
                    if (entity instanceof EntityItem) {
                        EntityItem item = (EntityItem) entity;
                        for (EntityPlayerMP player : droppedItems.keySet()) {
                            if (player != null && droppedItems.get(player).contains(item)) {
                                // skip over items that are set to never despawn
                                skipped++;
                                return;
                            }
                        }
                        item.setDead();
                        if (item.isDead) {
                            skipped++;
                        } else {
                            items++;
                        }
                    }
                });

                long elapsed = System.currentTimeMillis() - currentTime;

                List<EntityPlayer> players = event.world.playerEntities;

                players.forEach(player -> {
                    ITextComponent text = new TextComponentString(String.format(TextFormatting.GRAY + "%d items cleared, %d items skipped in %dms", items, skipped, elapsed));
                    player.sendStatusMessage(text, true);
                });

                skipped = 0;
                items = 0;
                seconds = 401;
            }
            seconds--;
            time = System.currentTimeMillis() / 1000;
        }
    }

    @SubscribeEvent
    public void onPickup(PlayerEvent.ItemPickupEvent event) {
        for (EntityPlayerMP player : droppedItems.keySet()) {
            Vector<EntityItem> items = droppedItems.get(player);
            for (EntityItem item : items) {
                if (item.getEntityId() == event.getOriginalEntity().getEntityId()) {
                    items.remove(item);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void entityConstructing(EntityEvent.EnteringChunk event) {
        if (event.getEntity() instanceof EntityItem) {
            EntityItem item = (EntityItem) event.getEntity();
            for (EntityPlayerMP player : droppedItems.keySet()) {
                if (droppedItems.get(player).contains(item)) {
                    return;
                }
            }
            World world = item.getEntityWorld();
            EntityPlayerMP player = (EntityPlayerMP) world.getClosestPlayerToEntity(item, 16);
            if (player != null) {
                if (!droppedItems.containsKey(player)) {
                    droppedItems.put(player, new Vector<>());
                }
                droppedItems.get(player).add(item);
            }
        }
    }

    @SubscribeEvent
    public void onDrop(ItemTossEvent event) {
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        if (player == null) {
            return;
        }
        if (!droppedItems.containsKey(player)) {
            droppedItems.put(player, new Vector<>());
        }
        EntityItem item = event.getEntityItem();
        droppedItems.get(player).add(item);
    }

}
