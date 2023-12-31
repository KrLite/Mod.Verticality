package net.krlite.verticality;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yurisuika.raised.client.option.RaisedConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.krlite.equator.math.algebra.Curves;
import net.krlite.equator.math.algebra.Theory;
import net.krlite.equator.visual.animation.Slice;
import net.krlite.equator.visual.animation.animated.AnimatedDouble;
import net.krlite.equator.visual.animation.interpolated.InterpolatedDouble;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class Verticality implements ModInitializer {
	public static final String NAME = "Verticality", ID = "verticality";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);
	public static final int
			HOTBAR_FULL_HEIGHT = 24, HOTBAR_HEIGHT = HOTBAR_FULL_HEIGHT - 1, SPECTATOR_BAR_HEIGHT = 22, SINGLE_BAR_HEIGHT = 5,
			ITEM_SIZE = 16, GAP = 2, HOTBAR_ITEM_GAP = (HOTBAR_FULL_HEIGHT - ITEM_SIZE) / 2 - 1,
			HOTBAR_WIDTH = 182, OFFHAND_WIDTH = 29,
			CENTER_DISTANCE_TO_BORDER = 11, TOOLTIP_OFFSET = 7;
	public static final float SCALAR = 1.5F, FONT_GAP_OFFSET = 0.5F;

	public static class Sounds {
		public static final SoundEvent GATE_LATCH = SoundEvent.of(new Identifier(ID, "gate_latch"));
		public static final SoundEvent LIGHT_SWITCH = SoundEvent.of(new Identifier(ID, "light_switch"));

		static void register() {
			Registry.register(Registries.SOUND_EVENT, GATE_LATCH.getId(), GATE_LATCH);
			Registry.register(Registries.SOUND_EVENT, LIGHT_SWITCH.getId(), LIGHT_SWITCH);
		}

		public static void playGateLatch() {
			play(GATE_LATCH);
		}

		public static void playLightSwitch() {
			play(LIGHT_SWITCH);
		}

		private static void play(SoundEvent sound) {
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
		}
	}

	private static final HotbarPreferences PREFERENCES = new HotbarPreferences();
	private static final AnimatedDouble
			transition = new AnimatedDouble(1, 0, 450, Curves.Back.OUT.reverse()),
			alternativeTransition = new AnimatedDouble(0, 1, 175, Curves.Exponential.Quadratic.EASE);
	private static final InterpolatedDouble
			offset = new InterpolatedDouble(0, 0.013),
			swap = new InterpolatedDouble(0, 0.015);
	private static final InterpolatedDouble
			later = new InterpolatedDouble(0, 0.013),
			earlier = new InterpolatedDouble(0, 0.013);
	private static boolean enabled, alternativeLayout;
	private static float spectatorMenuHeightScalar = 0;
	private static Supplier<Integer> raisedShift = () -> 0;

	static {
		transition.onPlay(() -> {
			transition.slice(Slice::reverse);
		});

		transition.onTermination(() -> {
			if (notCompleted()) {
				PREFERENCES.enabled(enabled);
				transition.play();
			}
		});

		alternativeTransition.onTermination(() -> {
			PREFERENCES.alternativeLayout(alternativeLayout);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null) {
				// Positive (offset)	: moving downwards
				// Zero (offset)		: no offset
				// Negative (offset)	: moving upwards
				if (!client.player.getOffHandStack().isEmpty()) {
					if (client.options.getMainArm().getValue().getOpposite() == Arm.LEFT) {
						offset.target(upsideDown() ? -1 : 1);
					}
					else offset.target(upsideDown() ? 1 : -1);
				}
				else offset.target(0);

				// Positive (swap)		: enable swap
				// Zero (swap)			: disable swap
				if (client.options.getMainArm().getValue().getOpposite() == Arm.LEFT) {
					swap.target(upsideDown() ? 1 : 0);
				}
				else swap.target(upsideDown() ? 0 : 1);
			}

			later.target(fullyEnabled() ? 1 : 0);
			earlier.target(fullyDisabled() ? 0 : 1);
		});

		transition.play();
		alternativeTransition.play();

		enabled = enabled();
		alternativeLayout = alternativeLayoutEnabled();
	}

	@Override
	public void onInitialize() {
		PREFERENCES.save();
		Sounds.register();

		if (isRaisedLoaded()) {
			raisedShift = RaisedConfig::getHud;
		}
	}

	public static boolean isRaisedLoaded() {
		return FabricLoader.getInstance().isModLoaded("raised");
	}

	public static boolean isAppleSkinLoaded() {
		return FabricLoader.getInstance().isModLoaded("appleskin");
	}

	public static int raisedShift() {
		return raisedShift.get();
	}

	public static int height() {
		return MinecraftClient.getInstance().getWindow().getScaledHeight();
	}

	public static int width() {
		return MinecraftClient.getInstance().getWindow().getScaledWidth();
	}

	public static double transition() {
		return transition.value();
	}

	public static double alternativeTransition() {
		return alternativeLayoutPartiallyEnabled() ? alternativeTransition.progress() : (alternativeTransition.end() - alternativeTransition.progress());
	}

	public static double alternativeLayoutOffsetX() {
		return enabled() ? 0 : -(width() - HOTBAR_WIDTH) / 2.0 * alternativeTransition();
	}

	public static double alternativeLayoutOffsetY() {
		return enabled() ? (height() - HOTBAR_WIDTH) / 2.0 * alternativeTransition() : 0;
	}

	public static double progress() {
		return notCompleted() ? transition.progress() : (1 - transition.progress());
	}

	public static double offset() {
		return Theory.lerp(offset.value(), 0, alternativeTransition());
	}

	public static double swap() {
		return swap.value();
	}

	public static double later() {
		return later.value();
	}

	public static double earlier() {
		return earlier.value();
	}

	public static boolean notCompleted() {
		return enabled() != enabled;
	}

	public static boolean unavailable() {
		return transition.isPlaying() || alternativeTransition.isPlaying();
	}

	public static boolean enabled() {
		return PREFERENCES.enabled();
	}

	public static boolean partiallyEnabled() {
		return enabled;
	}

	public static boolean fullyEnabled() {
		return partiallyEnabled() && enabled();
	}

	public static boolean fullyDisabled() {
		return !partiallyEnabled() && !enabled();
	}

	public static boolean alternativeLayoutEnabled() {
		return PREFERENCES.alternativeLayout();
	}

	public static boolean alternativeLayoutPartiallyEnabled() {
		return alternativeLayout;
	}

	public static boolean alternativeLayoutFullyEnabled() {
		return alternativeLayoutPartiallyEnabled() && alternativeLayoutEnabled();
	}

	public static boolean alternativeLayoutFullyDisabled() {
		return !alternativeLayoutPartiallyEnabled() && !alternativeLayoutEnabled();
	}

	public static boolean upsideDown() {
		return PREFERENCES.upsideDown();
	}

	public static boolean isSpectator() {
		return MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isSpectator();
	}

	public static float spectatorMenuHeightScalar() {
		return spectatorMenuHeightScalar;
	}

	public static void spectatorMenuHeightScalar(float scalar) {
		spectatorMenuHeightScalar = scalar;
	}

	public static void switchEnabled() {
		enabled = !enabled;
		transition.play();
	}

	public static void switchAlternativeMode() {
		alternativeLayout = !alternativeLayout;
		alternativeTransition.play();
	}

	public static void switchUpsideDown() {
		PREFERENCES.switchUpsideDown();
	}

	public static double hotbarShift() {
		return HOTBAR_HEIGHT + raisedShift();
	}

	public static boolean isMainArmLeft() {
		// Don't use 'MinecraftClient.getInstance().options.getMainArm()' as vanilla doesn't use it neither, otherwise causing the offhand item out-of-phase
		return MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getMainArm() == Arm.LEFT;
	}

	public static void translateIcon(DrawContext context, double y, boolean ignoreOffhand) {
		if (enabled()) {
			double offset = -2 * ((y + 8) - height() / 2.0);
			context.getMatrices().translate(
					-hotbarShift() * transition(),
					Theory.lerp(0, offset, swap()) + (ignoreOffhand ? 0 : (OFFHAND_WIDTH * offset() / 2)),
					0
			);

			// Compatibility with Raised
			context.getMatrices().translate(
					Verticality.raisedShift(),
					Verticality.raisedShift(),
					0
			);
		}
		else {
			context.getMatrices().translate(0, hotbarShift() * transition(), 0);
		}

		context.getMatrices().translate(alternativeLayoutOffsetX(), alternativeLayoutOffsetY(), 0);
	}

	public static void drawSelectedSlot(DrawContext context, Identifier identifier, int x, int y, int width, int height) {
		if (enabled()) {
			double offset = -2 * ((x + width / 2.0) - (int) (width() / 2.0));

			context.getMatrices().push();
			context.getMatrices().translate(
					Theory.lerp(0, offset, swap()),
					0, 0
			);
		}

		context.drawGuiTexture(identifier, x, y, width, height);

		if (enabled()) {
			context.getMatrices().pop();
		}
	}

	private static boolean blendWasEnabled = false;

	public static void verticallyShiftBarPre(DrawContext context, boolean hideInAlternativeLayout) {
		double transition = alternativeLayoutFullyEnabled() ? transition() : later();
		context.getMatrices().translate(0, hotbarShift() * transition, 0);

		if (hideInAlternativeLayout || enabled()) {
			blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
			RenderSystem.enableBlend();

			float alpha = (float) (1 - alternativeTransition());
			context.setShaderColor(1, 1, 1, alpha);
		}
	}

	public static void verticallyShiftBarPost(DrawContext context) {
		context.setShaderColor(1, 1, 1, 1);

		if (!blendWasEnabled) RenderSystem.disableBlend();
	}
}
