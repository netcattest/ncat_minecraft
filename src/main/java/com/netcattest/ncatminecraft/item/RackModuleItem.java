package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.client.rack.ClientRackAccess;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class RackModuleItem extends Item {
    private final RackModuleType type;

    public RackModuleItem(RackModuleType type) {
        super(new Item.Properties().stacksTo(16));
        this.type = type;
    }

    public RackModuleType type() { return type; }
    public RackModuleType getType() { return type; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), false);
        RackBlockEntity rack = RackBlockEntity.at(level, hit.getBlockPos());
        if (rack == null || player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (level.isClientSide)
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRackAccess.open(hit.getBlockPos()));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!rack.canConfigure(player)) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.rack.no_permission"), true);
            return InteractionResult.CONSUME;
        }
        int section = hit.getBlockPos().getY() - rack.getBlockPos().getY();
        int firstU = rack.firstUnitOfSection(section);
        int count = Math.max(1, rack.sectionUnits(section));
        double localY = Math.max(0D, Math.min(.9999D, hit.getLocation().y - hit.getBlockPos().getY()));
        int preferredU = firstU + Math.min(count - 1, (int) Math.floor(localY * count));
        int chosen = findFreePosition(rack, preferredU);
        if (chosen < 0 || !rack.installModule(type, chosen, player)) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.rack.no_space"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        player.displayClientMessage(Component.translatable("block.ncat_minecraft.rack.installed", chosen + 1), true);
        return InteractionResult.CONSUME;
    }

    private int findFreePosition(RackBlockEntity rack, int preferred) {
        int maxStart = rack.getCapacityU() - type.heightU();
        for (int start = Math.min(preferred, maxStart); start <= maxStart; start++)
            if (free(rack, start)) return start;
        for (int start = 0; start < preferred && start <= maxStart; start++)
            if (free(rack, start)) return start;
        return -1;
    }

    private boolean free(RackBlockEntity rack, int start) {
        if (!rack.fitsSegment(start, type.heightU())) return false;
        for (int unit = start; unit < start + type.heightU(); unit++)
            if (rack.moduleAtU(unit) != null) return false;
        return true;
    }
}
