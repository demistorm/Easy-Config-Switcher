package win.demistorm.easyconfigswitcher.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.PresetManager;
import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.config.Preset;

import java.util.ArrayList;
import java.util.List;

public final class ConfigScreen {

    private ConfigScreen() {
    }

    public static class EasyConfigSwitcherConfigScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private final Font font = client.font;

        private EditBox newPresetNameEdit;
        private Button createPresetButton;
        private PresetListWidget presetList;
        private EditBox titleLabelEdit;

        private static final int WIDGET_HEIGHT = 20;
        private static final int ENTRY_HEIGHT = 24;

        protected EasyConfigSwitcherConfigScreen(Screen parent) {
            super(Component.literal("Easy Config Switcher"));
            this.parent = parent;
        }

        public static EasyConfigSwitcherConfigScreen create(Screen parent) {
            return new EasyConfigSwitcherConfigScreen(parent);
        }

        @Override
        protected void init() {
            int topY = 45;

            newPresetNameEdit = new EditBox(font, width / 2 - 150, topY, 200, WIDGET_HEIGHT, Component.literal("Preset Name"));
            newPresetNameEdit.setMaxLength(PresetManager.getMaxNameLength());
            newPresetNameEdit.setHint(Component.literal("Enter preset name (max " + PresetManager.getMaxNameLength() + " chars)"));
            newPresetNameEdit.setResponder(value -> updateCreateButton());
            addRenderableWidget(newPresetNameEdit);

            createPresetButton = Button.builder(
                            Component.literal("Create Preset"),
                            btn -> createNewPreset())
                    .bounds(width / 2 + 60, topY, 90, WIDGET_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Create a new preset from current configs")))
                    .build();
            addRenderableWidget(createPresetButton);

            updateCreateButton();

            int listTopY = topY + 45;
            int listBottom = height - 50;
            presetList = new PresetListWidget(client, width, listTopY, listBottom);
            presetList.updateEntries();
            addWidget(presetList);

            titleLabelEdit = new EditBox(font, width / 2 - 200, height - 30, 160, WIDGET_HEIGHT, Component.literal("Title Label"));
            titleLabelEdit.setMaxLength(30);
            titleLabelEdit.setHint(Component.literal("Title screen label"));
            titleLabelEdit.setValue(ModConfig.getTitleLabel());
            addRenderableWidget(titleLabelEdit);

            Button doneButton = Button.builder(
                            Component.literal("Done"),
                            btn -> {
                                ModConfig.setTitleLabel(titleLabelEdit.getValue().trim());
                                ModConfig.save();
                                client.setScreen(parent);
                            })
                    .bounds(width / 2 + 10, height - 30, 140, WIDGET_HEIGHT)
                    .build();
            addRenderableWidget(doneButton);

            EasyConfigSwitcher.LOGGER.debug("Config screen initialized");
        }

        private void updateCreateButton() {
            createPresetButton.active = !newPresetNameEdit.getValue().trim().isEmpty();
        }

        private void createNewPreset() {
            String name = newPresetNameEdit.getValue().trim();
            if (name.isEmpty()) {
                EasyConfigSwitcher.LOGGER.warn("Cannot create preset with empty name");
                return;
            }

            String result = PresetManager.createPreset(name);
            EasyConfigSwitcher.LOGGER.info(result);

            if (result.contains("successfully")) {
                newPresetNameEdit.setValue("");
                updateCreateButton();
                presetList.updateEntries();
            }
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (newPresetNameEdit.isFocused() && (keyEvent.key() == 257 || keyEvent.key() == 335)) {
                createNewPreset();
                return true;
            }
            if (titleLabelEdit.isFocused() && (keyEvent.key() == 257 || keyEvent.key() == 335)) {
                ModConfig.setTitleLabel(titleLabelEdit.getValue().trim());
                ModConfig.save();
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            context.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);

            context.drawString(font, "Create New Preset:", width / 2 - 150, 35, 0xFFAAAAAA);

            context.drawString(font, "Title Screen Label:", width / 2 - 200, height - 42, 0xFFAAAAAA);

            if (presetList != null) {
                context.drawString(font, "Presets (" + presetList.children().size() + "/" + PresetManager.getMaxPresets() + ")",
                        width / 2 - 200, presetList.getY() - 15, 0xFFFFFFFF);
                presetList.render(context, mouseX, mouseY, delta);
            }

            super.render(context, mouseX, mouseY, delta);
        }

        private class PresetListWidget extends ObjectSelectionList<PresetListWidget.PresetEntry> {

            public PresetListWidget(Minecraft client, int width, int y, int bottom) {
                super(client, width, bottom - y, y, ENTRY_HEIGHT + 4);
            }

            public void updateEntries() {
                clearEntries();
                for (Preset preset : PresetManager.getAllPresets()) {
                    addEntry(new PresetEntry(preset));
                }
            }

            @Override
            public int getRowWidth() {
                return 400;
            }

            public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
                if (!isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) return false;
                for (PresetEntry entry : children()) {
                    if (entry.mouseClicked(mouseButtonEvent, bl)) return true;
                }
                return super.mouseClicked(mouseButtonEvent, bl);
            }

            public class PresetEntry extends ObjectSelectionList.Entry<PresetEntry> {
                final Preset preset;
                private final Button moveUpButton;
                private final Button moveDownButton;
                private final Button editTooltipButton;
                private final Button applyAndRestartButton;
                private final Button deleteButton;

                PresetEntry(Preset preset) {
                    this.preset = preset;

                    this.moveUpButton = Button.builder(
                                    Component.literal("↑"),
                                    btn -> moveEntry(-1))
                            .bounds(0, 0, 20, WIDGET_HEIGHT)
                            .tooltip(Tooltip.create(Component.literal("Move up")))
                            .build();

                    this.moveDownButton = Button.builder(
                                    Component.literal("↓"),
                                    btn -> moveEntry(1))
                            .bounds(0, 0, 20, WIDGET_HEIGHT)
                            .tooltip(Tooltip.create(Component.literal("Move down")))
                            .build();

                    this.editTooltipButton = Button.builder(
                                    Component.literal("Tooltip"),
                                    btn -> client.setScreen(new TooltipEditScreen(EasyConfigSwitcherConfigScreen.this, preset)))
                            .bounds(0, 0, 50, WIDGET_HEIGHT)
                            .tooltip(Tooltip.create(Component.literal("Edit custom tooltip text")))
                            .build();

                    this.applyAndRestartButton = Button.builder(
                                    Component.literal("Apply and restart"),
                                    btn -> {
                                        String result = PresetManager.applyOnRestart(preset.getName());
                                        if (result.startsWith("SHUTDOWN:")) {
                                            String message = result.substring("SHUTDOWN:".length());
                                            EasyConfigSwitcher.LOGGER.info(message);
                                            client.stop();
                                        }
                                    })
                            .bounds(0, 0, 100, WIDGET_HEIGHT)
                            .tooltip(Tooltip.create(Component.literal("Apply preset on next game start and restart")))
                            .build();

                    this.deleteButton = Button.builder(
                                    Component.literal("Delete"),
                                    btn -> {
                                        String result = PresetManager.deletePreset(preset.getName());
                                        EasyConfigSwitcher.LOGGER.info(result);
                                        presetList.updateEntries();
                                    })
                            .bounds(0, 0, 60, WIDGET_HEIGHT)
                            .tooltip(Tooltip.create(Component.literal("Delete this preset")))
                            .build();
                }

                private void moveEntry(int direction) {
                    List<PresetEntry> entries = new ArrayList<>(children());
                    int currentIndex = entries.indexOf(this);
                    int newIndex = currentIndex + direction;

                    if (newIndex >= 0 && newIndex < entries.size()) {
                        PresetListWidget.this.swap(currentIndex, newIndex);

                        List<String> newOrder = new ArrayList<>();
                        for (PresetEntry entry : children()) {
                            newOrder.add(entry.preset.getName());
                        }
                        PresetManager.reorderPresets(newOrder);
                    }
                }

                @Override
                public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                    int x = getX();
                    int y = getY();
                    int entryWidth = getWidth();
                    int entryHeight = getHeight();

                    context.fill(x, y, x + entryWidth, y + entryHeight - 2, 0x80000000);

                    List<PresetEntry> entries = children();
                    int currentIndex = entries.indexOf(this);

                    int buttonX = x + 5;
                    int buttonY = y + (entryHeight - WIDGET_HEIGHT) / 2;

                    moveUpButton.setPosition(buttonX, buttonY);
                    moveUpButton.active = currentIndex > 0;
                    moveUpButton.render(context, mouseX, mouseY, tickDelta);

                    moveDownButton.setPosition(buttonX + 22, buttonY);
                    moveDownButton.active = currentIndex < entries.size() - 1;
                    moveDownButton.render(context, mouseX, mouseY, tickDelta);

                    boolean isCurrent = preset.getName().equals(ModConfig.getCurrentPreset());
                    int nameColor = isCurrent ? 0xFF55FF55 : 0xFFFFFFFF;
                    String displayName = preset.getName();
                    if (isCurrent) {
                        displayName += " ✓";
                    }
                    context.drawString(font, displayName, x + 55, y + (entryHeight - 9) / 2, nameColor);

                    int rightButtonY = y + (entryHeight - WIDGET_HEIGHT) / 2;

                    int deleteX = x + entryWidth - 65;
                    deleteButton.setPosition(deleteX, rightButtonY);
                    deleteButton.render(context, mouseX, mouseY, tickDelta);

                    int restartX = deleteX - 105;
                    applyAndRestartButton.setPosition(restartX, rightButtonY);
                    applyAndRestartButton.render(context, mouseX, mouseY, tickDelta);

                    int tooltipX = restartX - 55;
                    editTooltipButton.setPosition(tooltipX, rightButtonY);
                    editTooltipButton.render(context, mouseX, mouseY, tickDelta);
                }

                @Override
                public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
                    if (moveUpButton.mouseClicked(mouseButtonEvent, bl)) return true;
                    if (moveDownButton.mouseClicked(mouseButtonEvent, bl)) return true;
                    if (editTooltipButton.mouseClicked(mouseButtonEvent, bl)) return true;
                    if (applyAndRestartButton.mouseClicked(mouseButtonEvent, bl)) return true;
                    return deleteButton.mouseClicked(mouseButtonEvent, bl);
                }

                @Override
                public Component getNarration() {
                    return Component.literal(preset.getName());
                }
            }
        }
    }
}
