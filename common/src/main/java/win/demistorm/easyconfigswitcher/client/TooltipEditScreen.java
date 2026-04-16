package win.demistorm.easyconfigswitcher.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.config.Preset;

public class TooltipEditScreen extends Screen {
    private final Screen parent;
    private final Preset preset;
    private final Minecraft client = Minecraft.getInstance();
    private final Font font = client.font;

    private MultiLineEditBox descriptionEdit;

    private static final int EDITBOX_WIDTH = 300;
    private static final int EDITBOX_HEIGHT = 160;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    public TooltipEditScreen(Screen parent, Preset preset) {
        super(Component.literal("Edit Tooltip"));
        this.parent = parent;
        this.preset = preset;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;

        int editX = centerX - EDITBOX_WIDTH / 2;
        int editY = centerY - EDITBOX_HEIGHT / 2;

        String currentDesc = preset.getDescription();
        if (currentDesc == null || currentDesc.isEmpty()) {
            currentDesc = "Click to apply " + preset.getName() + " preset";
        }

        descriptionEdit = new MultiLineEditBox(
                font,
                editX,
                editY,
                EDITBOX_WIDTH,
                EDITBOX_HEIGHT,
                Component.literal("Tooltip Description"),
                25,
                500
        );
        descriptionEdit.setValue(currentDesc);
        addRenderableWidget(descriptionEdit);

        Button saveButton = Button.builder(
                        Component.literal("Save"),
                        btn -> saveAndClose())
                .bounds(centerX - BUTTON_WIDTH - 5, editY + EDITBOX_HEIGHT + 15, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Save description and go back")))
                .build();
        addRenderableWidget(saveButton);

        Button cancelButton = Button.builder(
                        Component.literal("Cancel"),
                        btn -> client.setScreen(parent))
                .bounds(centerX + 5, editY + EDITBOX_HEIGHT + 15, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Cancel without saving")))
                .build();
        addRenderableWidget(cancelButton);

        EasyConfigSwitcher.LOGGER.debug("Tooltip edit screen initialized for preset: {}", preset.getName());
    }

    private void saveAndClose() {
        String newDescription = descriptionEdit.getFullValue().trim();

        if (newDescription.isEmpty()) {
            preset.setDescription(null);
        } else {
            preset.setDescription(newDescription);
        }

        ModConfig.save();

        EasyConfigSwitcher.LOGGER.info("Saved tooltip description for preset '{}': {}",
                preset.getName(),
                preset.getDescription() != null ? preset.getDescription() : "[default]");

        client.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0000000);

        String title = "Edit Tooltip: " + preset.getName();
        context.drawCenteredString(font, title, width / 2, height / 2 - EDITBOX_HEIGHT / 2 - 24, 0xFFFFFFFF);

        String tip = "This text appears when hovering the preset button on the title screen";
        context.drawCenteredString(font, tip, width / 2, height / 2 - EDITBOX_HEIGHT / 2 - 12, 0xFFAAAAAA);

        int charCount = descriptionEdit.getFullValue().length();
        String countText = "Characters: " + charCount + " / 500";
        int countColor = charCount > 450 ? 0xFFFF5555 : 0xFFAAAAAA;
        context.drawString(font, countText,
                width / 2 + EDITBOX_WIDTH / 2 - font.width(countText),
                height / 2 + EDITBOX_HEIGHT / 2 + 4,
                countColor);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == 256) {
            client.setScreen(parent);
            return true;
        }

        return super.keyPressed(keyEvent);
    }
}
