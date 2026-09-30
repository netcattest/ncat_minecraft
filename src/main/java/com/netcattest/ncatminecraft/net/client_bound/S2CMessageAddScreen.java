/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.DataPort;
import com.netcattest.ncatminecraft.entity.UsbPort;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.*;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

import java.io.IOException;
import java.util.ArrayList;

import static com.netcattest.ncatminecraft.block.ScreenBlock.hasTE;

public class S2CMessageAddScreen extends Packet {
	private boolean clear;
	private Vector3i pos;
	private ScreenData[] screens;
	
	public S2CMessageAddScreen(ScreenBlockEntity tes) {
		clear = true;
		pos = new Vector3i(tes.getBlockPos());
		screens = new ScreenData[tes.screenCount()];
		
		for (int i = 0; i < tes.screenCount(); i++)
			screens[i] = tes.getScreen(i);
	}
	
	public S2CMessageAddScreen(ScreenBlockEntity tes, ScreenData... toSend) {
		clear = false;
		pos = new Vector3i(tes.getBlockPos());
		screens = toSend;
	}
	
	public S2CMessageAddScreen(boolean clear, Vector3i pos, ScreenData[] screens) {
		this.clear = clear;
		this.pos = pos;
		this.screens = screens;
	}
	
	public S2CMessageAddScreen(FriendlyByteBuf buf) {
		super(buf);
		
		clear = buf.readBoolean();
		pos = new Vector3i(buf);
		
		int cnt = buf.readByte() & 7;
		
		screens = new ScreenData[cnt];
		for (int i = 0; i < cnt; i++) {
			screens[i] = new ScreenData();
			screens[i].side = BlockSide.values()[buf.readByte()];
			screens[i].size = new Vector2i(buf);
			screens[i].url = buf.readUtf();
			screens[i].resolution = new Vector2i(buf);
			screens[i].rotation = Rotation.values()[buf.readByte() & 3];
			screens[i].owner = new NameUUIDPair(buf);
			screens[i].friendRights = buf.readInt();
			screens[i].otherRights = buf.readInt();
			int friendCount = buf.readVarInt();
			if (friendCount < 0 || friendCount > 256) throw new IllegalArgumentException("Invalid screen friend count");
			screens[i].friends = new ArrayList<>(friendCount);
			for (int friend = 0; friend < friendCount; friend++)
				screens[i].friends.add(new NameUUIDPair(buf));
			screens[i].upgrades = new ArrayList<>();
			
			int numUpgrades = buf.readByte();
			for (int j = 0; j < numUpgrades; j++)
				screens[i].upgrades.add(buf.readItem());
			if (buf.readBoolean()) {
				screens[i].logSourcePos = new Vector3i(buf);
				screens[i].logSourceSide = BlockSide.fromInt(buf.readByte());
			}
			screens[i].logSourceViaCable = buf.readBoolean();
			int portCount = buf.readUnsignedByte();
			screens[i].dataPorts.clear();
			for (int p = 0; p < portCount; p++) {
				DataPort port = DataPort.load(buf.readNbt());
				if (port != null && screens[i].dataPorts.size() < 12) screens[i].dataPorts.add(port);
			}
			if (screens[i].dataPorts.stream().noneMatch(port -> port.automatic))
				screens[i].dataPorts.add(0, DataPort.automatic());
			int usbCount = buf.readUnsignedByte();
			screens[i].usbPorts.clear();
			for (int p = 0; p < usbCount; p++) {
				UsbPort port = UsbPort.load(buf.readNbt());
				if (port != null && screens[i].usbPorts.size() < 8) screens[i].usbPorts.add(port);
			}
			if (screens[i].usbPorts.stream().noneMatch(port -> port.automatic))
				screens[i].usbPorts.add(0, UsbPort.automatic());
		}
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(clear);
		pos.writeTo(buf);
		buf.writeByte(screens.length);
		
		for (ScreenData scr : screens) {
			buf.writeByte(scr.side.ordinal());
			scr.size.writeTo(buf);
			buf.writeUtf(scr.url);
			scr.resolution.writeTo(buf);
			buf.writeByte(scr.rotation.ordinal());
			scr.owner.writeTo(buf);
			buf.writeInt(scr.friendRights);
			buf.writeInt(scr.otherRights);
			int friendCount = Math.min(256, scr.friends == null ? 0 : scr.friends.size());
			buf.writeVarInt(friendCount);
			for (int friend = 0; friend < friendCount; friend++) scr.friends.get(friend).writeTo(buf);
			buf.writeByte(scr.upgrades.size());
			
			for (ItemStack is : scr.upgrades)
				buf.writeItem(is);
			buf.writeBoolean(scr.logSourcePos != null && scr.logSourceSide != null);
			if (scr.logSourcePos != null && scr.logSourceSide != null) {
				scr.logSourcePos.writeTo(buf);
				buf.writeByte(scr.logSourceSide.ordinal());
			}
			buf.writeBoolean(scr.logSourceViaCable);
			buf.writeByte(scr.dataPorts.size());
			for (DataPort port : scr.dataPorts) buf.writeNbt(port.save());
			buf.writeByte(scr.usbPorts.size());
			for (UsbPort port : scr.usbPorts) buf.writeNbt(port.save());
		}
	}
	
	public void handle(NetworkEvent.Context ctx) {
		if (checkClient(ctx)) {
			ctx.enqueueWork(() -> {
				Level lvl = (Level) NcatMinecraft.PROXY.getWorld(ctx);
				BlockEntity te = lvl.getBlockEntity(pos.toBlock());
				if (!(te instanceof ScreenBlockEntity)) {
					lvl.setBlockAndUpdate(pos.toBlock(), lvl.getBlockState(pos.toBlock()).setValue(hasTE, true));
					te = lvl.getBlockEntity(pos.toBlock());
					
					if (!(te instanceof ScreenBlockEntity)) {
						if (clear)
							Log.error("CMessageAddScreen: Can't add screen to invalid tile entity at %s", pos.toString());
						
						return;
					}
				}
				
				ScreenBlockEntity tes = (ScreenBlockEntity) te;
				if (clear)
					tes.clear();
				
				for (ScreenData entry : screens) {
					ScreenData scr = tes.addScreen(entry.side, entry.size, entry.resolution, null, false);
					boolean resized = scr.size.x != entry.size.x || scr.size.y != entry.size.y ||
							scr.resolution.x != entry.resolution.x || scr.resolution.y != entry.resolution.y;
					scr.size = entry.size;
					scr.resolution = entry.resolution;
					scr.rotation = entry.rotation;
					if (resized) {
						for (var browser : scr.browsers()) {
							if (browser instanceof MCEFBrowser mcefBrowser) {
								if (scr.rotation.isVertical) mcefBrowser.resize(scr.resolution.y, scr.resolution.x);
								else mcefBrowser.resize(scr.resolution.x, scr.resolution.y);
							}
						}
					}
					String webUrl;
					
					String page = BlockRegistry.screenPage(tes.getBlockState().getBlock());
					if (page != null)
						webUrl = page;
					else {
						try {
							webUrl = ScreenBlockEntity.url(entry.url);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
					
					boolean changedUrl = scr.syncedUrl == null || !scr.syncedUrl.equals(webUrl);
					scr.syncedUrl = webUrl;
					if (changedUrl || scr.url == null)
						scr.url = webUrl;
					scr.logSourcePos = entry.logSourcePos;
					scr.logSourceSide = entry.logSourceSide;
					scr.logSourceViaCable = entry.logSourceViaCable;
					scr.dataPorts.clear();
					scr.dataPorts.addAll(entry.dataPorts);
					scr.usbPorts.clear();
					scr.usbPorts.addAll(entry.usbPorts);
					scr.owner = entry.owner;
					scr.friendRights = entry.friendRights;
					scr.otherRights = entry.otherRights;
					scr.friends = entry.friends;
					scr.upgrades = entry.upgrades;
					
					if (scr.browser != null && changedUrl)
						scr.browser.loadURL(webUrl);
				}
				tes.updateAABB();
			});
			
			ctx.setPacketHandled(true);
		}
	}
}
