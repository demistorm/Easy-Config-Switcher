package win.demistorm.easyconfigswitcher.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.PresetManager;
import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.config.Preset;

import java.util.function.Consumer;

public class PresetButtonWidget {

    private final Preset preset;
    private final Button button;
    private final Consumer<String> onShutdown;
    private final Runnable onEnterConfirmState;

    private boolean inConfirmState = false;
    private long confirmStateStartTime = 0;
    private static final long CONFIRM_TIMEOUT_MS = 3000;

    public PresetButtonWidget(Preset preset, int x, int y, int width, int height, Consumer<String> onShutdown, Runnable onEnterConfirmState) {
        this.preset = preset;
        this.onShutdown = onShutdown;
        this.onEnterConfirmState = onEnterConfirmState;

        Component message;
        if (isActive()) {
            message = Component.literal(preset.getName()).withStyle(ChatFormatting.GREEN);
        } else {
            message = Component.literal(preset.getName());
        }

        this.button = Button.builder(
                message,
                btn -> handleClick()
        )
        .bounds(x, y, width, height)
        .tooltip(createNormalTooltip())
        .build();
    }

    private void handleClick() {
        if (inConfirmState) {
            String result = PresetManager.applyOnRestart(preset.getName());
            if (result.startsWith("SHUTDOWN:")) {
                String message = result.substring("SHUTDOWN:".length());
                onShutdown.accept(message);
            } else {
                EasyConfigSwitcher.LOGGER.info(result);
                exitConfirmState();
            }
        } else {
            enterConfirmState();
        }
    }

    private void enterConfirmState() {
        if (onEnterConfirmState != null) {
            onEnterConfirmState.run();
        }
        inConfirmState = true;
        confirmStateStartTime = System.currentTimeMillis();
        updateButtonState();
    }

    private void exitConfirmState() {
        inConfirmState = false;
        updateButtonState();
    }

    private void updateButtonState() {
        if (inConfirmState) {
            button.setMessage(Component.literal("Confirm?"));
            button.setTooltip(createConfirmTooltip());
        } else {
            if (isActive()) {
                button.setMessage(Component.literal(preset.getName()).withStyle(ChatFormatting.GREEN));
            } else {
                button.setMessage(Component.literal(preset.getName()));
            }
            button.setTooltip(createNormalTooltip());
        }
    }

    private Tooltip createNormalTooltip() {
        String desc = preset.getDescription();

        if (desc != null && !desc.isEmpty()) {
            return Tooltip.create(Component.literal(desc));
        }

        return Tooltip.create(Component.literal("Click to apply " + preset.getName() + " preset"));
    }

    private Tooltip createConfirmTooltip() {
        return Tooltip.create(Component.literal("Apply \"" + preset.getName() + "\" preset " +
                "(game will need to be restarted!)"));
    }

    public void tick() {
        if (inConfirmState && System.currentTimeMillis() - confirmStateStartTime > CONFIRM_TIMEOUT_MS) {
            exitConfirmState();
        }
    }

    public void onClickOutside() {
        if (inConfirmState) {
            exitConfirmState();
        }
    }

    public Button getButton() {
        return button;
    }

    public void reset() {
        exitConfirmState();
    }

    public boolean isActive() {
        return preset.getName().equals(ModConfig.getCurrentPreset());
    }
}
