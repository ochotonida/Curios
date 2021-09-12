/*
 * Copyright (c) 2018-2020 C4
 *
 * This file is part of Curios, a mod made for Minecraft.
 *
 * Curios is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Curios is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Curios.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.theillusivec4.curios;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.Map;
import javax.annotation.Nonnull;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.SlotTypePreset;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.client.ClientEventHandler;
import top.theillusivec4.curios.client.CuriosClientConfig;
import top.theillusivec4.curios.client.IconHelper;
import top.theillusivec4.curios.client.KeyRegistry;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.client.gui.GuiEventHandler;
import top.theillusivec4.curios.client.render.CuriosLayer;
import top.theillusivec4.curios.common.CuriosConfig;
import top.theillusivec4.curios.common.CuriosHelper;
import top.theillusivec4.curios.common.CuriosRegistry;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;
import top.theillusivec4.curios.common.capability.CurioItemCapability;
import top.theillusivec4.curios.common.event.CuriosEventHandler;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.slottype.SlotTypeManager;
import top.theillusivec4.curios.common.util.EquipCurioTrigger;
import top.theillusivec4.curios.server.SlotHelper;
import top.theillusivec4.curios.server.command.CurioArgumentType;
import top.theillusivec4.curios.server.command.CuriosCommand;

@Mod(Curios.MODID)
public class Curios {

  public static final String MODID = CuriosApi.MODID;
  public static final Logger LOGGER = LogManager.getLogger();

  public Curios() {
    final IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
    eventBus.addListener(this::setup);
    eventBus.addListener(this::config);
    eventBus.addListener(this::process);
    eventBus.addListener(this::registerCaps);
    MinecraftForge.EVENT_BUS.addListener(this::serverAboutToStart);
    MinecraftForge.EVENT_BUS.addListener(this::serverStopped);
    MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    MinecraftForge.EVENT_BUS.addListener(this::reload);
    ModLoadingContext.get().registerConfig(Type.CLIENT, CuriosClientConfig.CLIENT_SPEC);
    ModLoadingContext.get().registerConfig(Type.SERVER, CuriosConfig.SERVER_SPEC);
  }

  private void setup(FMLCommonSetupEvent evt) {
    CuriosApi.setCuriosHelper(new CuriosHelper());
    MinecraftForge.EVENT_BUS.register(new CuriosEventHandler());
    NetworkHandler.register();
    ArgumentTypes.register("curios:slot_type", CurioArgumentType.class,
        new EmptyArgumentSerializer<>(CurioArgumentType::slot));
    CriteriaTriggers.register(EquipCurioTrigger.INSTANCE);
  }

  private void registerCaps(RegisterCapabilitiesEvent evt) {
    evt.register(ICuriosItemHandler.class);
    evt.register(ICurio.class);
  }

  private void process(InterModProcessEvent evt) {
    SlotTypeManager.buildImcSlotTypes(evt.getIMCStream(SlotTypeMessage.REGISTER_TYPE::equals),
        evt.getIMCStream(SlotTypeMessage.MODIFY_TYPE::equals));
  }

  private void serverAboutToStart(FMLServerAboutToStartEvent evt) {
    CuriosApi.setSlotHelper(new SlotHelper());
    SlotTypeManager.buildSlotTypes();
  }

  private void serverStopped(FMLServerStoppedEvent evt) {
    CuriosApi.setSlotHelper(null);
  }

  private void registerCommands(RegisterCommandsEvent evt) {
    CuriosCommand.register(evt.getDispatcher());
  }

  private void reload(final AddReloadListenerEvent evt) {
    evt.addListener(new SimplePreparableReloadListener<Void>() {
      @Nonnull
      @Override
      protected Void prepare(@Nonnull ResourceManager resourceManagerIn,
                             @Nonnull ProfilerFiller profilerIn) {
        return null;
      }

      @Override
      protected void apply(@Nonnull Void objectIn, @Nonnull ResourceManager resourceManagerIn,
                           @Nonnull ProfilerFiller profilerIn) {
        CuriosEventHandler.dirtyTags = true;
      }
    });
  }

  private void config(final ModConfigEvent.Loading evt) {

    if (evt.getConfig().getModId().equals(MODID)) {

      if (evt.getConfig().getType() == Type.SERVER) {
        IConfigSpec<?> spec = evt.getConfig().getSpec();
        CommentedConfig commentedConfig = evt.getConfig().getConfigData();

        if (spec == CuriosConfig.SERVER_SPEC) {
          CuriosConfig.transformCurios(commentedConfig);
          SlotTypeManager.buildConfigSlotTypes();
        }
      }
    }
  }

  @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Bus.MOD)
  public static class ClientProxy {

    @SubscribeEvent
    public static void stitchTextures(TextureStitchEvent.Pre evt) {

      if (evt.getMap().location() == InventoryMenu.BLOCK_ATLAS) {

        for (SlotTypePreset preset : SlotTypePreset.values()) {
          evt.addSprite(
              new ResourceLocation(MODID, "item/empty_" + preset.getIdentifier() + "_slot"));
        }
        evt.addSprite(new ResourceLocation(MODID, "item/empty_cosmetic_slot"));
        evt.addSprite(new ResourceLocation(MODID, "item/empty_curio_slot"));
      }
    }

    @SubscribeEvent
    public static void setupClient(FMLClientSetupEvent evt) {
      CuriosApi.setIconHelper(new IconHelper());
      MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
      MinecraftForge.EVENT_BUS.register(new GuiEventHandler());
      MenuScreens.register(CuriosRegistry.CONTAINER_TYPE, CuriosScreen::new);
      KeyRegistry.registerKeys();
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers evt) {
      addPlayerLayer(evt, "default");
      addPlayerLayer(evt, "slim");
      CuriosRendererRegistry.load();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayer(EntityRenderersEvent.AddLayers evt, String skin) {
      EntityRenderer<? extends Player> renderer = evt.getSkin(skin);

      if (renderer instanceof LivingEntityRenderer livingRenderer) {
        livingRenderer.addLayer(new CuriosLayer<>(livingRenderer));
      }
    }
  }
}
