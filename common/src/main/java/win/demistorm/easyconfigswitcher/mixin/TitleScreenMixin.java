package win.demistorm.easyconfigswitcher.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.PresetManager;
import win.demistorm.easyconfigswitcher.client.PresetButtonWidget;
import win.demistorm.easyconfigswitcher.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Unique
    private final List<PresetButtonWidget> ecs$presetButtons = new ArrayList<>();

    @Unique
    private int ecs$labelY;

    @Unique
    private boolean ecs$buttonsAtTop;

    @Unique
    private static final int BUTTON_HEIGHT = 20;

    @Unique
    private static final int BUTTON_SPACING = 4;

    @Unique
    private static final int COLUMNS = 10;

    @Unique
    private static final int MAX_ROWS = 1;

    @Inject(method = "init", at = @At("RETURN"))
    private void ecs$addPresetButtons(CallbackInfo ci) {
        try {
            ecs$presetButtons.clear();

            var presets = PresetManager.getAllPresets();
            if (presets.isEmpty()) {
                EasyConfigSwitcher.LOGGER.debug("No presets configured, skipping title screen buttons");
                return;
            }

            Screen screen = (Screen) (Object) this;

            int buttonWidth = ecs$calculateButtonWidth(presets);

            ecs$buttonsAtTop = screen.height < 272;

            EasyConfigSwitcher.LOGGER.debug("Screen size: {}x{}, buttonsAtTop: {}", screen.width, screen.height, ecs$buttonsAtTop);

            int presetCount = Math.min(presets.size(), COLUMNS);
            int totalWidth = presetCount * buttonWidth + (presetCount - 1) * BUTTON_SPACING;
            int gridX = (screen.width - totalWidth) / 2;

            int gridY;
            if (ecs$buttonsAtTop) {
                gridY = 6;
                ecs$labelY = gridY - 4;
            } else {
                Font font = Minecraft.getInstance().font;
                int labelPadding = font.lineHeight + 6;
                gridY = screen.height - BUTTON_HEIGHT - labelPadding + 10;
                ecs$labelY = gridY - 12;
            }

            EasyConfigSwitcher.LOGGER.debug("Adding {} preset buttons at ({}, {}), button size: {}x{}",
                    presetCount, gridX, gridY, buttonWidth, BUTTON_HEIGHT);

            int row = 0;
            int col = 0;

            for (win.demistorm.easyconfigswitcher.config.Preset preset : presets) {
                if (row >= MAX_ROWS) break;

                int buttonX = gridX + col * (buttonWidth + BUTTON_SPACING);
                int buttonY = gridY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

                PresetButtonWidget widget = new PresetButtonWidget(
                        preset,
                        buttonX,
                        buttonY,
                        buttonWidth,
                        BUTTON_HEIGHT,
                        this::ecs$handleShutdown,
                        () -> {
                            for (PresetButtonWidget w : ecs$presetButtons) {
                                w.reset();
                            }
                        }
                );

                EasyConfigSwitcher.LOGGER.debug("Created preset button '{}' at ({}, {})", preset.getName(), buttonX, buttonY);

                ecs$presetButtons.add(widget);

                col++;
                if (col >= COLUMNS) {
                    col = 0;
                    row++;
                }
            }

            EasyConfigSwitcher.LOGGER.debug("Added {} preset buttons to title screen", ecs$presetButtons.size());

        } catch (Exception e) {
            EasyConfigSwitcher.LOGGER.error("Failed to add preset buttons to title screen", e);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ecs$onTick(CallbackInfo ci) {
        for (PresetButtonWidget widget : ecs$presetButtons) {
            widget.tick();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ecs$renderLabel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ecs$renderButtonsOnTop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ecs$presetButtons.isEmpty() && !ecs$buttonsAtTop) {
            Font font = Minecraft.getInstance().font;
            Component label = Component.literal(ModConfig.getTitleLabel());
            int labelWidth = font.width(label);
            int labelX = ((Screen) (Object) this).width / 2 - labelWidth / 2;
            guiGraphics.drawString(font, label, labelX, ecs$labelY, 0xFFFFFFFF);
        }

        for (PresetButtonWidget widget : ecs$presetButtons) {
            widget.getButton().render(guiGraphics, mouseX, mouseY, partialTick);
        }

        Minecraft mc = Minecraft.getInstance();
        double scaledMouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double scaledMouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        boolean overAnyButton = false;
        for (PresetButtonWidget widget : ecs$presetButtons) {
            if (widget.getButton().isMouseOver(scaledMouseX, scaledMouseY)) {
                overAnyButton = true;
                break;
            }
        }

        if (!overAnyButton && mc.mouseHandler.isLeftPressed()) {
            for (PresetButtonWidget widget : ecs$presetButtons) {
                widget.onClickOutside();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ecs$handleMouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        for (PresetButtonWidget widget : ecs$presetButtons) {
            if (widget.getButton().mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Unique
    private void ecs$handleShutdown(String message) {
        EasyConfigSwitcher.LOGGER.info("Shutting down game: {}", message);
        Minecraft.getInstance().stop();
    }

    @Unique
    private int ecs$calculateButtonWidth(List<win.demistorm.easyconfigswitcher.config.Preset> presets) {
        Font font = Minecraft.getInstance().font;
        int maxNameWidth = 0;
        for (win.demistorm.easyconfigswitcher.config.Preset preset : presets) {
            int nameWidth = font.width(preset.getName());
            if (nameWidth > maxNameWidth) {
                maxNameWidth = nameWidth;
            }
        }
        return maxNameWidth + 5;
    }
}
