package com.netcattest.ncatminecraft.registry;

import com.netcattest.ncatminecraft.entity.GamingChairSeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FurnitureEntityRegistry {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "ncat_minecraft");

    public static final RegistryObject<EntityType<GamingChairSeatEntity>> CHAIR_SEAT = TYPES.register("chair_seat", () ->
            EntityType.Builder.<GamingChairSeatEntity>of(GamingChairSeatEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(8)
                    .build("ncat_minecraft:chair_seat"));

    private FurnitureEntityRegistry() {
    }

    public static void init(IEventBus bus) {
        TYPES.register(bus);
    }
}
