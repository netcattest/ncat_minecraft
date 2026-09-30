package com.netcattest.ncatminecraft.controls;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.controls.builtin.*;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.Log;

import java.lang.reflect.Method;
import java.util.HashMap;

public class ScreenControlRegistry {
	private static final HashMap<ResourceLocation, ScreenControlType<?>> CONTROL_TYPES = new HashMap<>();
	
	public static void register(ResourceLocation name, ScreenControlType<?> type) {
		if (CONTROL_TYPES.containsKey(name)) {
			Log.warning("ScreenControlRegistry#CONTROL_TYPES already contains an entry with name " + name);
			throw new IllegalArgumentException("Cannot have two entries with the same name.");
		}
		CONTROL_TYPES.put(name, type);
		
		if (!FMLEnvironment.production) {
			if (FMLEnvironment.dist.isClient()) {
				boolean shouldThrow = false;
				try {
					Method m = type.clazz.getMethod("handleClient", BlockPos.class, BlockSide.class, ScreenBlockEntity.class, NetworkEvent.Context.class);
					OnlyIn onlyIn = m.getAnnotation(OnlyIn.class);
					if (onlyIn == null) shouldThrow = true;
					Dist d = onlyIn.value();
					if (d != Dist.CLIENT) shouldThrow = true;
				} catch (Throwable ignored) {
				}
				if (shouldThrow) {
					Log.warning("handleClient on ScreenControl classes MUST be marked with `@OnlyIn(Dist.CLIENT)`, but it is not on " + type.clazz);
					throw new IllegalStateException(
							"handleClient on ScreenControl classes MUST be marked with `@OnlyIn(Dist.CLIENT)`, but it is not on " + type.clazz
					);
				}
			}
		}
	}
	
	static {
		register(SetURLControl.id, new ScreenControlType<>(SetURLControl.class, SetURLControl::new));
		register(KeyTypedControl.id, new ScreenControlType<>(KeyTypedControl.class, KeyTypedControl::new));
		register(AutoVolumeControl.id, new ScreenControlType<>(AutoVolumeControl.class, AutoVolumeControl::new));
		register(JSRequestControl.id, new ScreenControlType<>(JSRequestControl.class, JSRequestControl::new));
		register(LaserControl.id, new ScreenControlType<>(LaserControl.class, LaserControl::new));
		register(ScreenModifyControl.id, new ScreenControlType<>(ScreenModifyControl.class, ScreenModifyControl::new));
		register(ModifyFriendListControl.id, new ScreenControlType<>(ModifyFriendListControl.class, ModifyFriendListControl::new));
		register(ManageRightsAndUpdgradesControl.id, new ScreenControlType<>(ManageRightsAndUpdgradesControl.class, ManageRightsAndUpdgradesControl::new));
		register(ClickControl.id, new ScreenControlType<>(ClickControl.class, ClickControl::new));
		register(OwnerControl.id, new ScreenControlType<>(OwnerControl.class, OwnerControl::new));
		register(TurnOffControl.id, new ScreenControlType<>(TurnOffControl.class, (buf) -> TurnOffControl.INSTANCE));
	}
	
	public static ScreenControl parse(FriendlyByteBuf buf) {
		return CONTROL_TYPES.get(new ResourceLocation(buf.readUtf()))
				.deserializer.apply(buf);
	}
	
	public static void init() {
	}
}
