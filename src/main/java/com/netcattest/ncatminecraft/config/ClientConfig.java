package com.netcattest.ncatminecraft.config;

import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.config.annoconfg.AnnoCFG;
import com.netcattest.ncatminecraft.config.annoconfg.annotation.format.*;
import com.netcattest.ncatminecraft.config.annoconfg.annotation.value.Default;
import com.netcattest.ncatminecraft.config.annoconfg.annotation.value.DoubleRange;
import com.netcattest.ncatminecraft.config.annoconfg.annotation.value.IntRange;

@Config(type = ModConfig.Type.CLIENT)
public class ClientConfig {
	@SuppressWarnings("unused")
	private static final AnnoCFG CFG = new AnnoCFG(FMLJavaModLoadingContext.get().getModEventBus(), ClientConfig.class);
	public static void init() {
	}

	@Name("terminal_enabled")
	@Comment({
			"Allows the NCAT Terminal screen to start a shell on THIS client machine.",
			"The session never leaves your computer and no other player can reach it.",
			"Turn this off if you do not want in-world screens able to launch shells."
	})
	@Translation("config.ncat_minecraft.terminal_enabled")
	@Default(valueBoolean = true)
	public static boolean terminalEnabled = true;

	@Name("terminal_directory")
	@Comment({
			"Working directory used when the terminal starts a shell.",
			"Leave empty to use your home folder."
	})
	@Translation("config.ncat_minecraft.terminal_directory")
	@Default(valueStr = "")
	public static String terminalDirectory = "";

	@Name("proxy_enabled")
	@Comment({
			"Allows the NCAT Proxy screen to sit in the request path of a linked browser screen.",
			"Only browsers running on THIS client are affected and no other player can reach it.",
			"Turn this off to leave the proxy screen in observe-only mode."
	})
	@Translation("config.ncat_minecraft.proxy_enabled")
	@Default(valueBoolean = true)
	public static boolean proxyEnabled = true;

	@Name("proxy_hold_seconds")
	@Comment({
			"How long a paused request waits for a decision before it is forwarded unchanged.",
			"This keeps a forgotten screen from hanging a page load forever."
	})
	@Translation("config.ncat_minecraft.proxy_hold_seconds")
	@IntRange(minV = 5, maxV = 600)
	@Default(valueI = 120)
	public static int proxyHoldSeconds = 120;

	@Name("proxy_max_body_kib")
	@Comment({
			"Largest request or response body the proxy keeps in memory, in KiB.",
			"Anything past this is truncated so a big download cannot fill the heap."
	})
	@Translation("config.ncat_minecraft.proxy_max_body_kib")
	@IntRange(minV = 16, maxV = 8192)
	@Default(valueI = 1024)
	public static int proxyMaxBodyKiB = 1024;
	
	@Name("load_distance")
	@Comment("How far (in blocks) you can be before a screen starts rendering")
	@Translation("config.ncat_minecraft.load_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 30)
	public static double loadDistance = 30.0;
	
	@Name("unload_distance")
	@Comment("How far you can be before a screen stops rendering")
	@Translation("config.ncat_minecraft.unload_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 32)
	public static double unloadDistance = 32.0;
	
	@Name("pad_resolution")
	@Comment({
			"The resolution that minePads should use",
			"Smaller values produce lower qualities, higher values produce higher qualities",
			"Due to how web browsers work however, the larger this value is, the smaller text is",
			"Also, higher values will invariably lag more",
			"A good goto value for this would be the height of your monitor, in pixels",
			"A standard monitor is (at least currently) 1080",
	})
	@Translation("config.ncat_minecraft.pad_res")
	@IntRange(minV = 0, maxV = Integer.MAX_VALUE)
	@Default(valueI = 720)
	public static int padResolution = 720;
	
	@Name("side_pad")
	@Comment({
			"When this is true, the minePad is placed off to the side of the screen when held, so it's visible but doesn't take up too much of the screen",
			"When this is false, the minePad is placed closer to the center of the screen, allow it to be seen better, but taking up more of your view",
	})
	@Translation("config.ncat_minecraft.side_pad")
	@Default(valueBoolean = true)
	public static boolean sidePad = true;

	@Comment({
			"Options relating to input handling"
	})
	@CFGSegment("input")
	public static class Input {
		@Name("keyboard_camera")
		@Comment({
				"If this is on, then the camera will try to focus on the selected element while a keyboard is in use",
				"Elsewise, it'll try to focus on the center of the screen",
		})
		@Translation("config.ncat_minecraft.keyboard_camera")
		@Default(valueBoolean = true)
		public static boolean keyboardCamera = true;
	}

	
	@SuppressWarnings("unused")
	public static void postLoad() {
		if (unloadDistance < loadDistance + 2.0)
			unloadDistance = loadDistance + 2.0;
		
		
		NcatMinecraft.INSTANCE.padResY = padResolution;
		NcatMinecraft.INSTANCE.padResX = NcatMinecraft.INSTANCE.padResY * NcatMinecraft.PAD_RATIO;
		
		NcatMinecraft.INSTANCE.unloadDistance2 = unloadDistance * unloadDistance;
		NcatMinecraft.INSTANCE.loadDistance2 = loadDistance * loadDistance;
		
	}
}
