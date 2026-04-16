package win.demistorm.easyconfigswitcher.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class MultiLineEditBox extends EditBox {
    private static final int LINE_HEIGHT = 20;
    private static final int DEFAULT_TEXT_COLOR = -2039584;
    private static final int DEFAULT_TEXT_COLOR_UNEDITABLE = -9408400;
    private static final int MAX_LINE_LENGTH = 49;

    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int scrollOffset = 0;

    private int highlightLine = 0;
    private int highlightColumn = 0;

    private boolean isDragging = false;
    private int dragAnchorLine = 0;
    private int dragAnchorColumn = 0;

    private final int maxLines;
    private final int visibleLines;
    private final Font font;
    private final int maxLength;
    private Component hint;

    public MultiLineEditBox(Font font, int x, int y, int width, int height, Component component, int maxLines, int maxLength) {
        super(font, x, y, width, height, component);
        this.font = font;
        this.visibleLines = height / LINE_HEIGHT;
        this.maxLines = maxLines;
        this.maxLength = maxLength;
        this.hint = null;

        setMaxLength(maxLength);

        this.setValue("");
    }

    @Override
    public void setHint(Component hint) {
        super.setHint(hint);
        this.hint = hint;
    }

    public String getFullValue() {
        return super.getValue();
    }

    private java.util.List<String> getLines() {
        String value = super.getValue();
        if (value.isEmpty()) {
            return java.util.Collections.singletonList("");
        }
        return java.util.Arrays.asList(value.split("\n", -1));
    }

    private String getCurrentLineText() {
        java.util.List<String> lines = getLines();
        if (cursorLine >= 0 && cursorLine < lines.size()) {
            return lines.get(cursorLine);
        }
        return "";
    }

    private int getColumnFromX(String lineText, int pixelX) {
        for (int i = 0; i < lineText.length(); i++) {
            if (font.width(lineText.substring(0, i + 1)) > pixelX) {
                return i;
            }
        }
        return lineText.length();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        if (isBordered()) {
            renderBackground(guiGraphics);
        }

        java.util.List<String> lines = getLines();

        int startLine = Math.max(0, scrollOffset);
        int endLine = Math.min(lines.size(), scrollOffset + visibleLines);

        int textColor = canConsumeInputImpl() ? DEFAULT_TEXT_COLOR : DEFAULT_TEXT_COLOR_UNEDITABLE;
        boolean shadow = true;

        if (hasHighlight()) {
            int selStartLine, selStartCol, selEndLine, selEndCol;
            if (cursorLine < highlightLine || (cursorLine == highlightLine && cursorColumn < highlightColumn)) {
                selStartLine = cursorLine;   selStartCol = cursorColumn;
                selEndLine   = highlightLine; selEndCol   = highlightColumn;
            } else {
                selStartLine = highlightLine; selStartCol = highlightColumn;
                selEndLine   = cursorLine;    selEndCol   = cursorColumn;
            }

            for (int i = Math.max(startLine, selStartLine); i <= Math.min(endLine - 1, selEndLine); i++) {
                String lineText = lines.get(i);
                int lineY = getY() + 4 + (i - scrollOffset) * LINE_HEIGHT;
                int textX = getX() + 4;

                int colFrom = (i == selStartLine) ? selStartCol : 0;
                int colTo   = (i == selEndLine)   ? selEndCol   : lineText.length();

                int xFrom = textX + font.width(lineText.substring(0, Math.min(colFrom, lineText.length())));
                int xTo   = textX + font.width(lineText.substring(0, Math.min(colTo,   lineText.length())));

                if (xTo == xFrom) xTo = xFrom + 2;
                guiGraphics.textHighlight(xFrom, lineY - 1, xTo, lineY + LINE_HEIGHT - 2, true);
            }
        }

        for (int i = startLine; i < endLine; i++) {
            String lineText = lines.get(i);
            int lineY = getY() + 4 + (i - scrollOffset) * LINE_HEIGHT;
            int textX = getX() + 4;

            if (!lineText.isEmpty()) {
                FormattedCharSequence seq = FormattedCharSequence.forward(lineText, Style.EMPTY);
                guiGraphics.drawString(font, seq, textX, lineY, textColor, shadow);
            }

            if (i == cursorLine && isFocused() && canConsumeInputImpl()) {
                long time = System.currentTimeMillis();
                boolean cursorVisible = (time / 300) % 2 == 0;

                if (cursorVisible) {
                    int safeColumn = Math.min(cursorColumn, lineText.length());
                    int cursorX = textX + font.width(lineText.substring(0, safeColumn));
                    int cursorTop = lineY - 1;
                    int cursorBottom = lineY + 10;

                    guiGraphics.fill(cursorX, cursorTop, cursorX + 1, cursorBottom, textColor);
                }
            }
        }

        if (lines.isEmpty() || (lines.size() == 1 && lines.getFirst().isEmpty())) {
            if (hint != null && !isFocused()) {
                guiGraphics.drawString(font, hint, getX() + 4, getY() + 4, DEFAULT_TEXT_COLOR, true);
            }
        }

        if (isHovered()) {
            guiGraphics.requestCursor(canConsumeInputImpl() ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
        }
    }

    private void renderBackground(GuiGraphics guiGraphics) {
        int borderColor = isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = 0xFF000000;

        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

        guiGraphics.hLine(getX(), getX() + getWidth() - 1, getY(), borderColor);
        guiGraphics.hLine(getX(), getX() + getWidth() - 1, getY() + getHeight() - 1, borderColor);
        guiGraphics.vLine(getX(), getY(), getY() + getHeight() - 1, borderColor);
        guiGraphics.vLine(getX() + getWidth() - 1, getY(), getY() + getHeight() - 1, borderColor);
    }

    private boolean hasHighlight() {
        return highlightLine != cursorLine || highlightColumn != cursorColumn;
    }

    private void resetHighlight() {
        highlightLine = cursorLine;
        highlightColumn = cursorColumn;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!canConsumeInputImpl()) {
            return false;
        }

        switch (keyEvent.key()) {
            case 257:
            case 335:
                if (canConsumeInputImpl()) {
                    insertNewline();
                }
                return true;

            case 262:
                moveCursorHorizontal(1);
                return true;

            case 263:
                moveCursorHorizontal(-1);
                return true;

            case 264:
                moveCursorDown();
                return true;

            case 265:
                moveCursorUp();
                return true;

            case 268:
                cursorColumn = 0;
                resetHighlight();
                return true;

            case 269:
                cursorColumn = getCurrentLineText().length();
                resetHighlight();
                return true;

            case 259:
                if (canConsumeInputImpl()) {
                    handleBackspace();
                }
                return true;

            case 261:
                if (canConsumeInputImpl()) {
                    handleDelete();
                }
                return true;

            default:
                if (keyEvent.isCopy()) {
                    copyHighlighted();
                    return true;
                } else if (keyEvent.isPaste()) {
                    if (canConsumeInputImpl()) {
                        pasteText();
                    }
                    return true;
                } else if (keyEvent.isCut()) {
                    copyHighlighted();
                    if (canConsumeInputImpl()) {
                        deleteHighlighted();
                    }
                    return true;
                }
                break;
        }

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (!canConsumeInputImpl()) {
            return false;
        }

        if (characterEvent.isAllowedChatCharacter() && canConsumeInputImpl()) {
            insertChar(characterEvent.codepointAsString());
            return true;
        }

        return false;
    }

    @Override
    public void onClick(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int clickX = (int) mouseButtonEvent.x() - getX();
        int clickY = (int) mouseButtonEvent.y() - getY();

        int clickedLine = scrollOffset + Math.max(0, (clickY - 4) / LINE_HEIGHT);

        java.util.List<String> lines = getLines();
        if (clickedLine >= 0 && clickedLine < lines.size()) {
            cursorLine = clickedLine;
            cursorColumn = getColumnFromX(lines.get(clickedLine), Math.max(0, clickX - 4));
        }

        dragAnchorLine = cursorLine;
        dragAnchorColumn = cursorColumn;
        isDragging = true;

        if (!doubleClick) {
            resetHighlight();
        }

        ensureCursorVisible();
        setFocused(true);
    }

    @Override
    protected void onDrag(MouseButtonEvent mouseButtonEvent, double dragX, double dragY) {
        if (!isDragging || !isFocused()) return;

        int relX = (int) mouseButtonEvent.x() - getX();
        int relY = (int) mouseButtonEvent.y() - getY();

        java.util.List<String> lines = getLines();
        int hoveredLine = Math.max(0, Math.min(
            scrollOffset + Math.max(0, (relY - 4) / LINE_HEIGHT),
            lines.size() - 1
        ));

        cursorLine = hoveredLine;
        cursorColumn = getColumnFromX(lines.get(hoveredLine), Math.max(0, relX - 4));
        highlightLine = dragAnchorLine;
        highlightColumn = dragAnchorColumn;

        ensureCursorVisible();
    }

    @Override
    public void onRelease(MouseButtonEvent mouseButtonEvent) {
        isDragging = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int delta = verticalAmount > 0 ? -1 : 1;
        int newOffset = scrollOffset + delta;

        java.util.List<String> lines = getLines();
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollOffset = Math.max(0, Math.min(newOffset, maxScroll));

        return true;
    }

    private void insertNewline() {
        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        if (lines.size() >= maxLines) {
            return;
        }

        String currentLine = cursorLine < lines.size() ? lines.get(cursorLine) : "";
        int safeColumn = Math.min(cursorColumn, currentLine.length());
        String beforeCursor = currentLine.substring(0, safeColumn);
        String afterCursor = currentLine.substring(safeColumn);

        if (cursorLine < lines.size()) {
            lines.set(cursorLine, beforeCursor);
        } else {
            lines.add(beforeCursor);
        }
        lines.add(cursorLine + 1, afterCursor);

        setValueFromLines(lines);

        cursorLine++;
        cursorColumn = 0;
        resetHighlight();
        ensureCursorVisible();
    }

    private void insertChar(String charStr) {
        if (hasHighlight()) {
            deleteHighlighted();
        }

        if (getFullValue().length() >= maxLength) {
            return;
        }

        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        while (lines.size() <= cursorLine) {
            lines.add("");
        }

        String currentLine = lines.get(cursorLine);
        int safeColumn = Math.min(cursorColumn, currentLine.length());

        if (currentLine.length() >= MAX_LINE_LENGTH) {
            return;
        }

        String beforeCursor = currentLine.substring(0, safeColumn);
        String afterCursor = currentLine.substring(safeColumn);

        lines.set(cursorLine, beforeCursor + charStr + afterCursor);

        setValueFromLines(lines);

        cursorColumn = safeColumn + 1;
        resetHighlight();
    }

    private void handleBackspace() {
        if (hasHighlight()) {
            deleteHighlighted();
            return;
        }

        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        if (cursorColumn > 0) {
            String currentLine = lines.get(cursorLine);
            int safeColumn = Math.min(cursorColumn, currentLine.length());

            String beforeCursor = currentLine.substring(0, safeColumn - 1);
            String afterCursor = currentLine.substring(safeColumn);

            lines.set(cursorLine, beforeCursor + afterCursor);
            cursorColumn--;
            resetHighlight();
        } else if (cursorLine > 0) {
            String prevLine = lines.get(cursorLine - 1);
            String currentLine = lines.get(cursorLine);

            lines.set(cursorLine - 1, prevLine + currentLine);
            lines.remove(cursorLine);

            cursorLine--;
            cursorColumn = prevLine.length();
            resetHighlight();
        }

        setValueFromLines(lines);
        ensureCursorVisible();
    }

    private void handleDelete() {
        if (hasHighlight()) {
            deleteHighlighted();
            return;
        }

        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        if (cursorColumn < lines.get(cursorLine).length()) {
            String currentLine = lines.get(cursorLine);
            int safeColumn = Math.min(cursorColumn, currentLine.length());

            String beforeCursor = currentLine.substring(0, safeColumn);
            String afterCursor = currentLine.substring(safeColumn + 1);

            lines.set(cursorLine, beforeCursor + afterCursor);
        } else if (cursorLine < lines.size() - 1) {
            String currentLine = lines.get(cursorLine);
            String nextLine = lines.get(cursorLine + 1);

            lines.set(cursorLine, currentLine + nextLine);
            lines.remove(cursorLine + 1);
            resetHighlight();
        }

        setValueFromLines(lines);
    }

    private SelectionBounds getSelectionBounds() {
        if (cursorLine < highlightLine || (cursorLine == highlightLine && cursorColumn < highlightColumn)) {
            return new SelectionBounds(cursorLine, cursorColumn, highlightLine, highlightColumn);
        }
        return new SelectionBounds(highlightLine, highlightColumn, cursorLine, cursorColumn);
    }

    private record SelectionBounds(int startLine, int startCol, int endLine, int endCol) {}

    private void deleteHighlighted() {
        if (!hasHighlight()) return;

        var sel = getSelectionBounds();
        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        String startLineText = lines.get(sel.startLine);
        String endLineText   = lines.get(sel.endLine);

        String before = startLineText.substring(0, Math.min(sel.startCol, startLineText.length()));
        String after  = endLineText.substring(Math.min(sel.endCol, endLineText.length()));

        if (sel.endLine >= sel.startLine + 1) {
            lines.subList(sel.startLine + 1, sel.endLine + 1).clear();
        }
        lines.set(sel.startLine, before + after);

        setValueFromLines(lines);

        cursorLine   = sel.startLine;
        cursorColumn = sel.startCol;
        resetHighlight();
        ensureCursorVisible();
    }

    private void copyHighlighted() {
        if (!hasHighlight()) return;

        var sel = getSelectionBounds();
        java.util.List<String> lines = getLines();
        StringBuilder sb = new StringBuilder();

        for (int i = sel.startLine; i <= sel.endLine; i++) {
            String lineText = lines.get(i);
            int from = (i == sel.startLine) ? sel.startCol : 0;
            int to   = (i == sel.endLine)   ? sel.endCol   : lineText.length();
            if (i > sel.startLine) sb.append("\n");
            sb.append(lineText, Math.min(from, lineText.length()), Math.min(to, lineText.length()));
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    private void pasteText() {
        if (hasHighlight()) {
            deleteHighlighted();
        }

        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard.isEmpty()) {
            return;
        }

        String[] pasteLines = clipboard.split("\n", -1);

        java.util.List<String> lines = new java.util.ArrayList<>(getLines());

        while (lines.size() <= cursorLine) {
            lines.add("");
        }

        String currentLine = lines.get(cursorLine);
        int safeColumn = Math.min(cursorColumn, currentLine.length());

        if (pasteLines.length == 1) {
            String beforeCursor = currentLine.substring(0, safeColumn);
            String afterCursor = currentLine.substring(safeColumn);
            String newText = beforeCursor + pasteLines[0] + afterCursor;

            if (newText.length() <= MAX_LINE_LENGTH) {
                lines.set(cursorLine, newText);
                cursorColumn += pasteLines[0].length();
            } else {
                int remainingSpace = MAX_LINE_LENGTH - beforeCursor.length() - afterCursor.length();
                String truncated = pasteLines[0].substring(0, Math.max(0, remainingSpace));
                lines.set(cursorLine, beforeCursor + truncated + afterCursor);
                cursorColumn += truncated.length();
            }
        } else {
            String beforeCursor = currentLine.substring(0, safeColumn);
            String afterCursor = currentLine.substring(safeColumn);

            String firstLine = beforeCursor + pasteLines[0];
            if (firstLine.length() > MAX_LINE_LENGTH) {
                return;
            }
            lines.set(cursorLine, firstLine);

            for (int i = 1; i < pasteLines.length - 1 && lines.size() < maxLines; i++) {
                String lineToAdd = pasteLines[i].length() <= MAX_LINE_LENGTH
                    ? pasteLines[i]
                    : pasteLines[i].substring(0, MAX_LINE_LENGTH);
                lines.add(cursorLine + i, lineToAdd);
            }

            String lastLine = pasteLines[pasteLines.length - 1] + afterCursor;
            if (lastLine.length() > MAX_LINE_LENGTH) {
                lastLine = lastLine.substring(0, MAX_LINE_LENGTH);
            }
            int insertPos = cursorLine + Math.min(pasteLines.length - 1, maxLines - lines.size() - 1);
            if (lines.size() < maxLines) {
                lines.add(insertPos, lastLine);
                cursorLine = insertPos;
                cursorColumn = lastLine.length() - afterCursor.length();
            }
        }

        setValueFromLines(lines);
        ensureCursorVisible();
    }

    private void setValueFromLines(java.util.List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i));
        }
        super.setValue(sb.toString());
    }

    private void moveCursorHorizontal(int delta) {
        String currentLine = getCurrentLineText();
        int newColumn = cursorColumn + delta;

        if (newColumn >= 0 && newColumn <= currentLine.length()) {
            cursorColumn = newColumn;
        } else if (newColumn < 0 && cursorLine > 0) {
            cursorLine--;
            cursorColumn = getLines().get(cursorLine).length();
            ensureCursorVisible();
        } else if (newColumn > currentLine.length() && cursorLine < getLines().size() - 1) {
            cursorLine++;
            cursorColumn = 0;
            ensureCursorVisible();
        }

        resetHighlight();
    }

    private void moveCursorUp() {
        if (cursorLine > 0) {
            cursorLine--;
            String lineText = getCurrentLineText();
            cursorColumn = Math.min(cursorColumn, lineText.length());
            ensureCursorVisible();
        }
        resetHighlight();
    }

    private void moveCursorDown() {
        if (cursorLine < getLines().size() - 1) {
            cursorLine++;
            String lineText = getCurrentLineText();
            cursorColumn = Math.min(cursorColumn, lineText.length());
            ensureCursorVisible();
        }
        resetHighlight();
    }

    private void ensureCursorVisible() {
        java.util.List<String> lines = getLines();
        int maxScroll = Math.max(0, lines.size() - visibleLines);

        if (cursorLine < scrollOffset) {
            scrollOffset = Math.max(0, cursorLine);
        } else if (cursorLine >= scrollOffset + visibleLines) {
            scrollOffset = Math.min(maxScroll, cursorLine - visibleLines + 1);
        }
    }

    private boolean canConsumeInputImpl() {
        return isActive() && isFocused() && this.active;
    }
}
