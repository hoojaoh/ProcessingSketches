package applet.juicygui;

import processing.core.PApplet;
import processing.core.PVector;
import processing.event.MouseEvent;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings({"InnerClassMayBeStatic", "SameParameterValue", "FieldCanBeLocal", "BooleanMethodIsAlwaysInverted", "unused", "ConstantConditions", "WeakerAccess"})
public class JuicyGuiSketch extends PApplet {
    //TODO:
    // ----- Operation JUICE -----
    // gui elements:
    // gamepad support
    // juicy special buttons, cool animations
    // juicy buttons, visually clear on/off states
    // hue picker slider
    // 2D vector unit grid picker
    // ...
    // other features:
    // action click animation for extra juice
    // scrolling down to allow unlimited group and element count

    // state
    private static final String GROUP_MARKER = "GROUP_MARKER";
    private static final String SEPARATOR = " _ ";
    private static final String ACTION_MIDDLE_MOUSE_BUTTON = "MIDDLE_MOUSE_BUTTON";
    private static final String ACTION_UP = "UP";
    private static final String ACTION_DOWN = "DOWN";
    private static final String ACTION_LEFT = "LEFT";
    private static final String ACTION_RIGHT = "RIGHT";
    private static final String ACTION_PRECISION_ZOOM_IN = "PRECISION_ZOOM_IN";
    private static final String ACTION_PRECISION_ZOOM_OUT = "PRECISION_ZOOM_OUT";
    private static final String ACTION_RESET = "RESET";
    // utils
    protected float t;
    private ArrayList<ArrayList<String>> undoStack = new ArrayList<ArrayList<String>>();
    private ArrayList<ArrayList<String>> redoStack = new ArrayList<ArrayList<String>>();
    // color
    private float backgroundAlpha = .5f;
    private boolean onPC;
    private ArrayList<Group> groups = new ArrayList<Group>();
    private Group currentGroup = null; // do not access directly!
    private float grayscaleGrid = .3f;
    private float grayscaleTextSelected = 1;
    private float grayscaleText = .6f;
    // input
    private ArrayList<Key> keyboardKeys = new ArrayList<Key>();
    private ArrayList<Key> keyboardKeysToRemove = new ArrayList<Key>();
    private String keyboardAction = "";
    private int keyboardSelectionIndex = 0;
    private int specialButtonCount = 3;
    private int keyRepeatDelayFirst = 300;
    private int keyRepeatDelay = 40;
    private boolean pMousePressed = false;

    // layout
    private float textSize = 24;
    private float cell = 40;
    private float minimumTrayWidth = cell * 6;
    private float trayWidth = minimumTrayWidth;
    private boolean trayVisible = true;
    private float animationEasingFactor = 3;

    // overlay
    private boolean overlayVisible;
    private Element overlayOwner; // do not assign directly!
    private int overlayOwnershipAnimationDuration = 10;
    private int overlayOwnershipAnimationStarted = -overlayOwnershipAnimationDuration;
    private float overlayDarkenEasingFactor = 3;


    // INTERFACE

    protected float sliderFloat(String name, float defaultValue, float precision) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(name, currentGroup.name)) {
            SliderFloat newElement = new SliderFloat(currentGroup, name, defaultValue, precision);
            currentGroup.elements.add(newElement);
        }
        SliderFloat slider = (SliderFloat) findElement(name, currentGroup.name);
        assert slider != null;
        return slider.value;
    }

    protected PVector slider2D(String name, float defaultX, float defaultY, float precision) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(name, currentGroup.name)) {
            Slider2D newElement = new Slider2D(currentGroup, name, defaultX, defaultY, precision);
            currentGroup.elements.add(newElement);
        }
        Slider2D slider = (Slider2D) findElement(name, currentGroup.name);
        assert slider != null;
        return slider.value;
    }

    protected int sliderColor(String name, float hue, float sat, float br) {
        return sliderColor(name, hue, sat, br, 1.f);
    }

    protected int sliderColor(String name, float hue, float sat, float br, float alpha) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(name, currentGroup.name)) {
            SliderColor newElement = new SliderColor(currentGroup, name, hue, sat, br, alpha);
            currentGroup.elements.add(newElement);
        }
        SliderColor slider = (SliderColor) findElement(name, currentGroup.name);
        assert slider != null;
        return slider.value;
    }

    protected String radio(String defaultValue, String... otherValues) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(defaultValue, currentGroup.name)) {
            Element newElement = new Radio(currentGroup, defaultValue, otherValues);
            currentGroup.elements.add(newElement);
        }
        Radio radio = (Radio) findElement(defaultValue, currentGroup.name);
        assert radio != null;
        return radio.options.get(radio.valueIndex);
    }

    protected boolean button(String name) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(name, currentGroup.name)) {
            Button newElement = new Button(currentGroup, name);
            currentGroup.elements.add(newElement);
        }
        Button button = (Button) findElement(name, currentGroup.name);
        return button.value;
    }

    protected boolean toggle(String name) {
        return toggle(name, false);
    }

    protected boolean toggle(String name, boolean defaultState) {
        Group currentGroup = getCurrentGroup();
        if (!elementExists(name, currentGroup.name)) {
            Toggle newElement = new Toggle(currentGroup, name, defaultState);
            currentGroup.elements.add(newElement);
        }
        Toggle toggle = (Toggle) findElement(name, currentGroup.name);
        return toggle.state;
    }

    protected void gui() {
        gui(true);
    }

    protected void gui(boolean defaultVisibility) {
        t += radians(1);
        if (frameCount == 1) {
            firstFrameSetup(defaultVisibility);
            return;
        }
        updateKeyboardInput();
        pushStyle();
        strokeCap(SQUARE);
        colorMode(HSB, 1, 1, 1, 1);
        resetMatrixInAnyRenderer();
        updateTrayBackground();
        updateSpecialButtons();
        updateGroupsAndTheirElements();
        updateFps();
        if (overlayVisible) {
            overlayOwner.updateOverlay();
        }
        popStyle();
        pMousePressed = mousePressed;
        if (!keyPressed || (!keyboardAction.equals("") && !keyboardAction.equals(ACTION_LEFT) && !keyboardAction.equals(ACTION_RIGHT))) {
            keyboardAction = "";
        }
    }

    private void firstFrameSetup(boolean defaultVisibility) {
        //the maximum text size we want to ever use needs to be called first, otherwise the font is stretched and ugly
        textSize(textSize * 2);
        trayVisible = defaultVisibility;
        onPC = System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    // UTILS

    private boolean isPointInRect(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    protected float ease(float p, float g) {
        if (p < 0.5)
            return 0.5f * pow(2 * p, g);
        else
            return 1 - 0.5f * pow(2 * (1 - p), g);
    }

    // TRAY

    private void updateFps() {
        if (!trayVisible) {
            return;
        }
        int nonFlickeringFrameRate = floor(frameRate > 55 ? 60 : frameRate);
        String fps = nonFlickeringFrameRate + " fps";
        surface.setTitle(this.getClass().getSimpleName() + "\t" + fps);
    }

    private void updateSpecialButtons() {
        float x = 0;
        float y = 0;
        updateSpecialHideButton(x, y, cell * 2, cell);
        if (!trayVisible) {
            return;
        }
        x += cell * 2;
        updateSpecialUndoButton(x, y, cell * 2, cell);
        x += cell * 2;
        updateSpecialRedoButton(x, y, cell * 2, cell);
    }

    private void updateSpecialHideButton(float x, float y, float w, float h) {
        if (activated("hide/show", x, y, w, h)) {
            trayVisible = !trayVisible;
            keyboardSelectionIndex = 0;
        }
        fill((keyboardSelected("hide/show") || isMouseOver(x, y, w, h)) ? grayscaleTextSelected : grayscaleText);
        if (isMouseOver(x, y, w, h) || trayVisible) {
            textSize(textSize);
            textAlign(CENTER, BOTTOM);
            text(trayVisible ? "hide" : "show", x, y, w, h);
        }
    }

    private void updateSpecialUndoButton(float x, float y, float w, float h) {
        boolean canUndo = undoStack.size() > 0;
        if (canUndo && (activated("undo", x, y, w, h) || keyboardAction.equals("UNDO"))) {
            pushTopUndoToRedo();
            popUndoToCurrentState();
            keyboardSelectionIndex = 1;
        }
        textSize(textSize);
        textAlign(CENTER, BOTTOM);
        fill((keyboardSelected("undo") || isMouseOver(x, y, w, h)) ? grayscaleTextSelected : grayscaleText);
        text("undo", x, y, w, h);
        textSize(textSize * .5f);
        textAlign(CENTER, CENTER);
        text(undoStack.size(), x + w * .9f, y + textSize * .4f);
    }

    private void updateSpecialRedoButton(float x, float y, float w, float h) {
        boolean canRedo = redoStack.size() > 0;
        if (canRedo && (activated("redo", x, y, w, h) || keyboardAction.equals("REDO"))) {
            pushTopRedoOntoUndo();
            popRedoToCurrentState();
            keyboardSelectionIndex = 2;
        }
        textSize(textSize);
        textAlign(CENTER, BOTTOM);
        fill((keyboardSelected("redo") || isMouseOver(x, y, w, h)) ? grayscaleTextSelected : grayscaleText);
        text("redo", x, y, w, h);
        textSize(textSize * .5f);
        textAlign(CENTER, CENTER);
        text(redoStack.size(), x + w * .9f, y + textSize * .4f);
    }

    protected void group(String name) {
        Group group = findGroup(name);
        if (!groupExists(name)) {
            group = new Group(name);
            groups.add(group);
        }
        setCurrentGroup(group);
    }

    private void updateGroupsAndTheirElements() {
        if (!trayVisible) {
            return;
        }
        float x = cell * .5f;
        float y = cell * 3;
        for (Group group : groups) {
            updateGroup(group, x, y);
            if (group.expanded) {
                x += cell * .5f;
                for (Element el : group.elements) {
                    y += cell;
                    el.handleKeyboardInput();
                    updateElement(group, el, x, y);
                }
                x -= cell * .5f;
            }
            y += cell;
        }
    }

    private void updateGroup(Group group, float x, float y) {
        fill((keyboardSelected(group.name) || isMouseOver(0, y - cell, trayWidth, cell)) ? grayscaleTextSelected : grayscaleText);
        textAlign(LEFT, BOTTOM);
        textSize(textSize);
        text(group.name, x, y);
        if (!overlayVisible && activated(group.name, 0, y - cell, trayWidth, cell)) {
            group.expanded = !group.expanded;
        }
    }

    private void updateElement(Group group, Element el, float x, float y) {
        float grayScale = keyboardSelected(group.name + el.name) || isMouseOver(0, y - cell, trayWidth, cell) ? grayscaleTextSelected : grayscaleText;
        fill(grayScale);
        stroke(grayScale);
        el.displayOnTray(x, y);
        el.update();
        if (activated(group.name + el.name, 0, y - cell, trayWidth, cell)) {
            if (!el.canHaveOverlay()) {
                el.onActivationWithoutOverlay(0, y - cell, trayWidth, cell);
                return;
            }
            if (!overlayVisible) {
                setOverlayOwner(el);
                overlayVisible = true;
            } else if (overlayVisible && !el.equals(overlayOwner)) {
                setOverlayOwner(el);
            } else if (overlayVisible && el.equals(overlayOwner)) {
                overlayOwner.onOverlayHidden();
                overlayVisible = false;
            }
        }
    }

    private void updateTrayBackground() {
        if (!trayVisible) {
            return;
        }
        textSize(textSize);
        trayWidth = max(minimumTrayWidth, findLongestNameWidth() + cell * 2);
        noStroke();
        fill(0, backgroundAlpha);
        rectMode(CORNER);
        rect(0, 0, trayWidth, height);
    }

    private void drawTrayGrid() {
        stroke(grayscaleGrid);
        for (float x = cell; x < trayWidth; x += cell) {
            line(x, 0, x, height);
        }
        for (float y = cell; y < height; y += cell) {
            line(0, y, trayWidth, y);
        }
    }

    private void resetMatrixInAnyRenderer() {
        if (sketchRenderer().equals(P3D)) {
            camera();
        } else {
            resetMatrix();
        }
    }

    private void setOverlayOwner(Element overlayOwner) {
        if (this.overlayOwner != null) {
            this.overlayOwner.onOverlayHidden();
        }
        this.overlayOwner = overlayOwner;
        this.overlayOwner.onOverlayShown();
        overlayVisible = true;
        overlayOwnershipAnimationStarted = frameCount;
    }

    // INPUT

    private boolean activated(String query, float x, float y, float w, float h) {
        return mouseJustReleasedHere(x, y, w, h) || keyboardActivated(query);
    }

    private boolean keyboardActivated(String query) {
        return keyboardSelected(query) && keyboardAction.equals("ACTION");
    }

    private boolean mouseJustReleasedHere(float x, float y, float w, float h) {
        return mouseJustReleased() && isPointInRect(mouseX, mouseY, x, y, w, h);
    }

    private boolean mouseJustReleased() {
        return pMousePressed && !mousePressed;
    }

    private boolean mouseJustPressed() {
        return !pMousePressed && mousePressed;
    }

    private boolean mouseJustPressedOutsideGui() {
        return !pMousePressed && mousePressed && isMouseOutsideGui();
    }

    private boolean isMouseOutsideGui() {
        return !isPointInRect(mouseX, mouseY, 0, 0, trayWidth, height);
    }

    private boolean isMouseOver(float x, float y, float w, float h) {
        return onPC && (frameCount > 1) && isPointInRect(mouseX, mouseY, x, y, w, h);
    }

    public void mouseReleased() {
        if (mouseButton == CENTER) {
            keyboardAction = ACTION_MIDDLE_MOUSE_BUTTON;
        }
    }

    public void mouseWheel(MouseEvent event) {
        float direction = event.getCount();
        if (direction > 0) {
            keyboardAction = ACTION_PRECISION_ZOOM_IN;
        } else if (direction < 0) {
            keyboardAction = ACTION_PRECISION_ZOOM_OUT;
        }
    }

    private boolean isAnyGroupKeyboardSelected() {
        return findKeyboardSelectedGroup() != null;
    }

    private boolean isAnyElementKeyboardSelected() {
        return findKeyboardSelectedElement() != null;
    }

    private boolean keyboardSelected(String query) {
        if (!trayVisible) {
            keyboardSelectionIndex = 0;
        }
        if ((query.equals("hide/show") && keyboardSelectionIndex == 0)
                || (query.equals("undo") && keyboardSelectionIndex == 1)
                || (query.equals("redo") && keyboardSelectionIndex == 2)) {
            return true;
        }
        int i = specialButtonCount;
        for (Group group : groups) {
            if (group.name.equals(query) && keyboardSelectionIndex == i) {
                return true;
            }
            i++;
            for (Element el : group.elements) {
                if ((group.name + el.name).equals(query) && keyboardSelectionIndex == i) {
                    return true;
                }
                i++;
            }
        }
        return false;
    }

    private int keyboardSelectableItemCount() {
        int elementCount = 0;
        for (Group group : groups) {
            elementCount += group.elements.size();
        }
        return specialButtonCount + groups.size() + elementCount;
    }

    public void keyPressed() {
        if (key == CODED) {
            if (!isKeyPressed(keyCode, true)) {
                keyboardKeys.add(new Key(keyCode, true));
            }
        } else {
            if (!isKeyPressed(key, false)) {
                keyboardKeys.add(new Key((int) key, false));
            }
        }
    }

    private boolean isKeyPressed(int keyCode, boolean coded) {
        for (Key kk : keyboardKeys) {
            if (kk.character == keyCode && kk.coded == coded) {
                return true;
            }
        }
        return false;
    }

    public void keyReleased() {
        if (key == CODED) {
            removeKey(keyCode, true);
        } else {
            removeKey(key, false);
        }
    }

    private void removeKey(int keyCodeToRemove, boolean coded) {
        keyboardKeysToRemove.clear();
        for (Key kk : keyboardKeys) {
            if (kk.coded == coded && kk.character == keyCodeToRemove) {
                keyboardKeysToRemove.add(kk);
            }
        }
        keyboardKeys.removeAll(keyboardKeysToRemove);
    }

    private void updateKeyboardInput() {
        for (Key kk : keyboardKeys) {
            if (kk.coded) {
                if (kk.character == UP) {
                    keyboardAction = ACTION_UP;
                    if (kk.repeatCheck()) {
                        if (keyboardSelectionIndex == specialButtonCount) {
                            keyboardSelectionIndex = 0;
                        } else {
                            keyboardSelectionIndex -= hiddenElementCount(false);
                            keyboardSelectionIndex--;
                        }
                        if (isAnyElementKeyboardSelected()) {
                            setOverlayOwner(findKeyboardSelectedElement());
                        }
                    }
                }
                if (kk.character == DOWN) {
                    keyboardAction = ACTION_DOWN;
                    if (kk.repeatCheck()) {
                        if (keyboardSelectionIndex < specialButtonCount) {
                            keyboardSelectionIndex = specialButtonCount;
                        } else {
                            keyboardSelectionIndex += hiddenElementCount(true);
                            keyboardSelectionIndex++;
                        }
                        if (isAnyElementKeyboardSelected()) {
                            setOverlayOwner(findKeyboardSelectedElement());
                        }
                    }
                }
                if (kk.character == LEFT) {
                    if (isAnyGroupKeyboardSelected() && findKeyboardSelectedGroup().expanded) {
                        Group keyboardSelected = findKeyboardSelectedGroup();
                        keyboardSelected.expanded = !keyboardSelected.expanded;
                    } else {
                        keyboardAction = ACTION_LEFT;
                    }
                }
                if (kk.character == RIGHT) {
                    if (isAnyGroupKeyboardSelected() && !findKeyboardSelectedGroup().expanded) {
                        Group keyboardSelected = findKeyboardSelectedGroup();
                        keyboardSelected.expanded = !keyboardSelected.expanded;
                    } else {
                        keyboardAction = ACTION_RIGHT;
                    }
                }
            } else if (!kk.coded) {
                if (!kk.justPressed) {
                    continue;
                }
                if (kk.character == '*' || kk.character == '+') {
                    keyboardAction = ACTION_PRECISION_ZOOM_OUT;
                }
                if (kk.character == '/' || kk.character == '-') {
                    keyboardAction = ACTION_PRECISION_ZOOM_IN;
                }
                if (kk.character == ' ' || kk.character == ENTER) {
                    keyboardAction = "ACTION";
                }
                if (kk.character == 'r') {
                    keyboardAction = ACTION_RESET;
                }
                if (kk.character == 'z' || kk.character == 26) {
                    keyboardAction = "UNDO";
                }
                if (kk.character == 'y') {
                    keyboardAction = "REDO";
                }
            }
            kk.justPressed = false;
        }
        keyboardSelectionIndex %= keyboardSelectableItemCount();
        if (keyboardSelectionIndex < 0) {
            Group lastGroup = getLastGroup();
            if (!lastGroup.expanded) {
                keyboardSelectionIndex = keyboardSelectableItemCount() - lastGroup.elements.size() - 1;
            } else {
                keyboardSelectionIndex = keyboardSelectableItemCount() - 1;
            }
        }
    }

    private float findLongestNameWidth() {
        float longestNameWidth = 0;
        for (Group group : groups) {
            for (Element el : group.elements) {
                if (el.trayTextWidth() > longestNameWidth) {
                    longestNameWidth = el.trayTextWidth();
                }
            }
        }
        return longestNameWidth;
    }

    // GROUPS AND ELEMENTS

    private int hiddenElementCount(boolean forwardFacing) {
        if (isAnyGroupKeyboardSelected()) {
            Group group;
            if (forwardFacing) {
                group = findKeyboardSelectedGroup();
            } else {
                group = getPreviousGroup(findKeyboardSelectedGroup().name);
            }
            if (!group.expanded) {
                return group.elements.size();
            }
        }
        return 0;
    }

    private Group getCurrentGroup() {
        if (currentGroup == null) {
            Group anonymous = new Group("");
            groups.add(anonymous);
            currentGroup = anonymous;
        }
        return currentGroup;
    }

    private void setCurrentGroup(Group currentGroup) {
        this.currentGroup = currentGroup;
    }

    private Group getLastGroup() {
        return groups.get(groups.size() - 1);
    }

    private Group findGroup(String name) {
        for (Group group : groups) {
            if (group.name.equals(name)) {
                return group;
            }
        }
        return null;
    }

    private boolean groupExists(String name) {
        return findGroup(name) != null;
    }

    private Group getPreviousGroup(String query) {
        for (Group group : groups) {
            if (group.name.equals(query)) {
                int index = groups.indexOf(group);
                if (index > 0) {
                    return groups.get(index - 1);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private Group getNextGroup(String query) {
        for (Group group : groups) {
            if (group.name.equals(query)) {
                int index = groups.indexOf(group);
                if (index < groups.size() - 1) {
                    return groups.get(index + 1);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private Group findKeyboardSelectedGroup() {
        for (Group group : groups) {
            if (keyboardSelected(group.name)) {
                return group;
            }
        }
        return null;
    }

    private Element findKeyboardSelectedElement() {
        for (Group group : groups) {
            for (Element el : group.elements)
                if (keyboardSelected(group.name + el.name)) {
                    return el;
                }
        }
        return null;
    }

    private boolean elementExists(String elementName, String groupName) {
        return findElement(elementName, groupName) != null;
    }

    private Element findElement(String elementName, String groupName) {
        for (Group g : groups) {
            for (Element el : g.elements) {
                if (g.name.equals(groupName) && el.name.equals(elementName)) {
                    return el;
                }
            }
        }
        return null;
    }

    // STATE

    private void popUndoAndForget() {
        if (undoStack.size() == 0) {
            return;
        }
        undoStack.remove(undoStack.size() - 1);
    }

    private void pushCurrentStateToUndo() {
        undoStack.add(getGuiState());
        redoStack.clear();
    }

    private void pushTopUndoToRedo() {
        if (undoStack.size() == 0) {
            throw new IllegalStateException("nothing to find in undo stack!");
        }
        redoStack.add(undoStack.get(undoStack.size() - 1));
    }

    private void pushTopRedoOntoUndo() {
        if (redoStack.size() == 0) {
            throw new IllegalStateException("nothing to find in undo stack!");
        }
        undoStack.add(redoStack.get(redoStack.size() - 1));
    }

    private void popUndoToCurrentState() {
        if (undoStack.size() == 0) {
            throw new IllegalStateException("nothing to pop in undo stack!");
        }
        overwriteState(undoStack.remove(undoStack.size() - 1));
    }

    private void popRedoToCurrentState() {
        if (redoStack.size() == 0) {
            throw new IllegalStateException("nothing to pop in redo stack!");
        }
        overwriteState(redoStack.remove(redoStack.size() - 1));
    }

    private void overwriteState(ArrayList<String> newStates) {
        for (String newState : newStates) {
            if (newState.startsWith(GROUP_MARKER)) {
                Group group = findGroup(newState.split(SEPARATOR)[1]);
                group.overwriteState(newState);
            } else {
                String[] splitState = newState.split(SEPARATOR);
                Element el = findElement(splitState[1], splitState[0]);
                el.overwriteState(newState);
            }
        }
    }

    private ArrayList<String> getGuiState() {
        ArrayList<String> state = new ArrayList<String>();
        for (Group group : groups) {
            state.add(group.getState());
            for (Element el : group.elements) {
                state.add(el.getState());
            }
        }
        return state;
    }

    class Key {
        boolean justPressed;
        boolean repeatedAlready = false;
        boolean coded;
        int character;
        int lastRegistered = -1;

        Key(Integer character, boolean coded) {
            this.character = character;
            this.coded = coded;
            justPressed = true;
        }

        boolean repeatCheck() {
            boolean shouldApply = justPressed ||
                    (!repeatedAlready && millis() > lastRegistered + keyRepeatDelayFirst) ||
                    (repeatedAlready && millis() > lastRegistered + keyRepeatDelay);
            if (shouldApply) {
                lastRegistered = millis();
                if (!justPressed) {
                    repeatedAlready = true;
                }
            }
            justPressed = false;
            return shouldApply;
        }
    }

    // GROUPS and control ELEMENTS

    class Group {
        String name;
        boolean expanded = true;
        ArrayList<Element> elements = new ArrayList<Element>();

        Group(String name) {
            this.name = name;
        }

        String getState() {
            return GROUP_MARKER + SEPARATOR + name + SEPARATOR + expanded;
        }

        void overwriteState(String newState) {
            this.expanded = Boolean.parseBoolean(newState.split(SEPARATOR)[2]);
        }
    }

    abstract class Element {
        Group parent;
        String name;

        Element(Group parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        String getState() {
            return parent.name + SEPARATOR + name + SEPARATOR;
        }

        abstract void overwriteState(String newState);

        abstract boolean canHaveOverlay();

        void update() {

        }

        void updateOverlay() {

        }

        void onActivationWithoutOverlay(int x, float y, float w, float h) {

        }

        void displayOnTray(float x, float y) {
            textAlign(LEFT, BOTTOM);
            textSize(textSize);
            if (this.equals(overlayOwner)) {
                underlineAnimation(overlayOwnershipAnimationStarted, overlayOwnershipAnimationDuration, x, y, true);
            }
            text(name, x, y);
        }

        float trayTextWidth() {
            return textWidth(name);
        }

        void onOverlayShown() {

        }

        void onOverlayHidden() {

        }

        void underlineAnimation(int startFrame, int duration, float x, float y, boolean stayExtended) {
            float fullWidth = textWidth(name);
            float animation = constrain(norm(frameCount, startFrame, startFrame + duration), 0, 1);
            float animationEased = ease(animation, animationEasingFactor);
            if (!stayExtended && animation == 1) {
                animation = 0;
            }
            float w = fullWidth * animation;
            float centerX = x + fullWidth * .5f;
            strokeWeight(2);
            line(centerX - w * .5f, y, centerX + w * .5f, y);
        }

        void handleKeyboardInput() {

        }
    }

    class Radio extends Element {
        int valueIndex = 0;
        ArrayList<String> options = new ArrayList<String>();
        int indexWhenOverlayShown = 0;

        Radio(Group parent, String name, String[] options) {
            super(parent, name);
            this.options.add(name);
            this.options.addAll(Arrays.asList(options));
        }

        String getState() {
            StringBuilder state = new StringBuilder(super.getState() + valueIndex);
            for (String option : options) {
                state.append(SEPARATOR);
                state.append(option);
            }
            return state.toString();
        }

        void overwriteState(String newState) {
            valueIndex = Integer.parseInt(newState.split(SEPARATOR)[2]);
        }

        boolean canHaveOverlay() {
            return false;
        }

        void onOverlayShown() {
            indexWhenOverlayShown = valueIndex;
        }

        boolean stateChangedWhileOverlayWasShown() {
            return valueIndex == indexWhenOverlayShown;
        }

        String value() {
            return options.get(valueIndex);
        }

        void displayOnTray(float x, float y) {
            textAlign(LEFT, BOTTOM);
            textSize(textSize);
            pushStyle();
            float optionX = x;
            for (int i = 0; i < options.size(); i++) {
                String option = options.get(i);
                float textWidthWithoutSeparator = textWidth(option);
                if (i < options.size() - 1) {
                    option += " ";
                }
                if (i != valueIndex) {
                    float strikethroughY = y - textSize * .5f;
                    strokeWeight(2);
                    line(optionX, strikethroughY, optionX + textWidthWithoutSeparator, strikethroughY);
                }
                text(option, optionX, y);
                optionX += textWidth(option);
            }
            popStyle();
        }

        void onActivationWithoutOverlay(int x, float y, float w, float h) {
            pushCurrentStateToUndo();
            valueIndex++;
            if (valueIndex >= options.size()) {
                valueIndex = 0;
            }
        }
    }

    class Button extends Element {
        boolean value;
        int activationAnimationDuration = 10;
        int activationAnimationStarted = -activationAnimationDuration;

        Button(Group parent, String name) {
            super(parent, name);
        }

        void overwriteState(String newState) {

        }

        boolean canHaveOverlay() {
            return false;
        }

        void onActivationWithoutOverlay(int x, float y, float w, float h) {
            value = true;
        }

        void displayOnTray(float x, float y) {
            if (value) {
                activationAnimationStarted = frameCount;
            }
            super.displayOnTray(x, y);
            underlineAnimation(activationAnimationStarted, activationAnimationDuration, x, y, false);
        }

        void update() {
            value = false;
        }
    }

    class Toggle extends Element {
        boolean state, defaultState;

        Toggle(Group parent, String name, boolean defaultState) {
            super(parent, name);
            this.defaultState = defaultState;
            this.state = defaultState;
        }

        String getState() {
            return super.getState() + state;
        }

        void overwriteState(String newState) {
            this.state = Boolean.parseBoolean(newState.split(SEPARATOR)[2]);
        }

        boolean canHaveOverlay() {
            return false;
        }

        void displayOnTray(float x, float y) {
            if (state) {
                // TODO display this differently, it clashes with the slider overlay indicator
                strokeWeight(2);
                line(x, y, x + textWidth(name), y);
            }
            text(name, x, y);
            super.displayOnTray(x, y);
        }

        void update() {
            if (keyboardAction.equals(ACTION_RESET)) {
                state = defaultState;
            }
        }

        void onActivationWithoutOverlay(int x, float y, float w, float h) {
            pushCurrentStateToUndo();
            state = !state;
        }
    }

    abstract class Slider extends Element {
        Slider(Group parent, String name) {
            super(parent, name);
        }

        float updateInfiniteSlider(float precision, float sliderWidth, boolean horizontallyControlled, boolean mouseWheelHorizontal) {
            if (mousePressed && isMouseOutsideGui()) {
                float screenSpaceDelta = horizontallyControlled ? (pmouseX - mouseX) : (pmouseY - mouseY);
                float valueSpaceDelta = screenDistanceToValueDistance(screenSpaceDelta, precision, sliderWidth);
                if (abs(valueSpaceDelta) > 0) {
                    return valueSpaceDelta;
                }
            }
            if (keyboardAction.equals(ACTION_LEFT)) {
                return screenDistanceToValueDistance(-3, precision, sliderWidth);
            } else if (keyboardAction.equals(ACTION_RIGHT)) {
                return screenDistanceToValueDistance(3, precision, sliderWidth);
            } else if (keyboardAction.equals(ACTION_UP)) {
                return screenDistanceToValueDistance(3, precision, sliderWidth);
            } else if (keyboardAction.equals(ACTION_DOWN)) {
                return screenDistanceToValueDistance(3, precision, sliderWidth);
            }
            return 0;
        }

        float screenDistanceToValueDistance(float screenSpaceDelta, float precision, float sliderWidth) {
            float valueToScreenRatio = precision / sliderWidth;
            return screenSpaceDelta * valueToScreenRatio;
        }

        void displayInfiniteSliderCenterMode(float x, float y, float w, float h, float precision, float value, boolean horizontal) {
            pushMatrix();
            pushStyle();
            translate(x, y);
            noStroke();
            drawSliderBackground(w, h, !horizontal);
            strokeWeight(3);
            drawHorizontalLine(w);
            if (!horizontal) {
                pushMatrix();
                scale(-1, 1);
            }
            drawMarkerLines(precision * 0.5f, h * .5f, true, value, precision, w, h, !horizontal);
            drawMarkerLines(precision * .05f, h * .3f, false, value, precision, w, h, !horizontal);
            if (!horizontal) {
                popMatrix();
            }
            drawValue(h, precision, value);
            popMatrix();
            popStyle();
        }

        void drawSliderBackground(float w, float h, boolean verticalCutout) {
            fill(0, backgroundAlpha);
            rectMode(CENTER);
            rect(0, 0, verticalCutout?w-h*2:w,h);
        }

        void drawHorizontalLine(float w) {
            stroke(grayscaleText);
            beginShape();
            for (int i = 0; i < w; i++) {
                float iNorm = norm(i, 0, w);
                float screenX = lerp(-w, w, iNorm);
                stroke(1, distanceFromCenterGrayscale(screenX, w));
                vertex(screenX, 0);
            }
            endShape();
        }

        void drawMarkerLines(float frequency, float markerHeight, boolean shouldDrawValue, float value, float precision, float w, float h, boolean flipTextHorizontally) {
            float markerValue = -precision - value;
            while (markerValue <= precision - value) {
                float markerNorm = norm(markerValue, -precision - value, precision - value);
                drawMarkerLine(markerValue, precision, w, h, markerHeight, value, shouldDrawValue, flipTextHorizontally);
                markerValue += frequency;
            }
        }

        private void drawMarkerLine(float markerValue, float precision, float w, float h, float markerHeight, float value, boolean shouldDrawValue, boolean flipTextHorizontally) {
            float moduloValue = markerValue;
            while (moduloValue > precision) {
                moduloValue -= precision * 2;
            }
            while (moduloValue < -precision) {
                moduloValue += precision * 2;
            }
            float screenX = map(moduloValue, -precision, precision, -w, w);
            float grayscale = distanceFromCenterGrayscale(screenX, w);
            fill(1,grayscale);
            stroke(1,grayscale);
            line(screenX, -markerHeight * .5f, screenX, 0);
            if (shouldDrawValue) {
                if (flipTextHorizontally) {
                    pushMatrix();
                    scale(-1, 1);
                }
                float displayValue = moduloValue + value;
                String displayText = nf(displayValue, 0, 0);
                if (displayText.equals("-0")) {
                    displayText = "0";
                }
                pushMatrix();
                textAlign(CENTER, CENTER);
                textSize(textSize);
                float textX = screenX + ((displayText.equals("0") || displayValue > 0) ? 0 : -textWidth("-") * .5f);
                text(displayText, flipTextHorizontally ? -textX : textX, h*.25f);
                if (flipTextHorizontally) {
                    popMatrix();
                }
                popMatrix();
            }
        }

        void drawValue(float sliderHeight, float precision, float value) {
            fill(grayscaleText);
            textAlign(CENTER, CENTER);
            textSize(textSize * 2);
            float textY = -cell * 3;
            float textX = 0;
            String text = nf(value, 0, 2);
            if (text.startsWith("-")) {
                textX -= textWidth("-") * .5f;
            }
            noStroke();
            fill(0, .5f);
            rectMode(CENTER);
            rect(textX, textY + textSize * .33f, textWidth(text) + 20, textSize * 2 + 20);
            fill(grayscaleTextSelected);
            text(text, textX, textY);
        }

        float distanceFromCenterGrayscale(float screenX, float w) {
            float xNorm = norm(screenX, -w, w);
            float distanceFromCenter = abs(.5f - xNorm) * 4;
            return 1 - ease(distanceFromCenter, overlayDarkenEasingFactor);
        }
    }

    class SliderFloat extends Slider {
        float value, precision, defaultValue, defaultPrecision, minValue, maxValue, lastValueDelta, valueWhenMouseInteractionStarted;
        boolean constrained = false;

        SliderFloat(Group parent, String name, float defaultValue, float precision) {
            super(parent, name);
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.precision = precision;
            this.defaultPrecision = precision;
            if (name.equals("fill") || name.equals("stroke")) {
                constrained = true;
                minValue = 0;
                maxValue = 255;
            }
        }

        void handleKeyboardInput() {
            if (keyboardAction.equals(ACTION_PRECISION_ZOOM_OUT) && precision > .1f) {
                pushCurrentStateToUndo();
                precision *= .1f;
            }
            if (keyboardAction.equals(ACTION_PRECISION_ZOOM_IN) && precision < 100000) {
                pushCurrentStateToUndo();
                precision *= 10f;
            }
            if (keyboardAction.equals(ACTION_RESET)) {
                pushCurrentStateToUndo();
                precision = defaultPrecision;
                value = defaultValue;
            }
        }

        String getState() {
            return super.getState() + value + SEPARATOR + precision;
        }

        void overwriteState(String newState) {
            String[] state = newState.split(SEPARATOR);
            value = Float.parseFloat(state[2]);
            precision = Float.parseFloat(state[3]);
        }

        boolean canHaveOverlay() {
            return true;
        }

        void onOverlayShown() {

        }

        void updateOverlay() {
            float valueDelta = updateInfiniteSlider(precision, width, true, true);
            recordInteractionForUndo(valueDelta);
            value += valueDelta;
            lastValueDelta = valueDelta;
            if (constrained) {
                value = constrain(value, minValue, maxValue);
            }
            displayInfiniteSliderCenterMode(width * .5f, height - cell, width, cell * 2, precision, value, true);
        }

        void recordInteractionForUndo(float valueDelta) {
            if (!mouseJustPressedOutsideGui() && lastValueDelta == 0 && valueDelta != 0) {
                pushCurrentStateToUndo();
            }
        }
    }

    class Slider2D extends Slider {
        PVector value = new PVector();
        PVector defaultValue = new PVector();
        PVector valueWhenMouseInteractionStarted = new PVector();
        PVector lastValueDelta = new PVector();
        float precision, defaultPrecision;
        private boolean mouseWheelHorizontal = false;

        Slider2D(Group currentGroup, String name, float defaultX, float defaultY, float precision) {
            super(currentGroup, name);
            this.precision = precision;
            this.defaultPrecision = precision;
            value.x = defaultX;
            value.y = defaultY;
            defaultValue.x = defaultX;
            defaultValue.y = defaultY;
        }

        String getState() {
            return super.getState() + value.x + SEPARATOR + value.y;
        }

        void overwriteState(String newState) {
            String[] xyz = newState.split(SEPARATOR);
            value.x = Float.parseFloat(xyz[2]);
            value.y = Float.parseFloat(xyz[3]);
        }

        boolean canHaveOverlay() {
            return true;
        }

        void updateOverlay() {
            PVector valueDelta = new PVector();
            valueDelta.x = updateInfiniteSlider(precision, width, true, mouseWheelHorizontal);
            displayInfiniteSliderCenterMode(width * .5f, height - cell, width, cell * 2, precision, value.x, true);
            translate(width * .5f, height * .5f);
            rotate(-HALF_PI);
            translate(-height * .5f, -width * .5f);
            valueDelta.y = updateInfiniteSlider(precision, width, false, mouseWheelHorizontal);
            displayInfiniteSliderCenterMode(height * .5f, width - cell, height, cell * 2, precision, value.y, false);
            recordInteractionForUndo(valueDelta);
            value.x += valueDelta.x;
            value.y += valueDelta.y;
            lastValueDelta.x = valueDelta.x;
            lastValueDelta.y = valueDelta.y;
        }

        void recordInteractionForUndo(PVector valueDelta) {
            if (mouseJustPressedOutsideGui()) {
                pushCurrentStateToUndo();
            }
        }

        void handleKeyboardInput() {
            if (keyboardAction.equals(ACTION_PRECISION_ZOOM_OUT) && precision > .1f) {
                pushCurrentStateToUndo();
                precision *= .1f;
            }
            if (keyboardAction.equals(ACTION_PRECISION_ZOOM_IN) && precision < 10000) {
                pushCurrentStateToUndo();
                precision *= 10f;
            }
            if (keyboardAction.equals(ACTION_RESET)) {
                pushCurrentStateToUndo();
                precision = defaultPrecision;
                value.x = defaultValue.x;
                value.y = defaultValue.y;
            }
            if (keyboardAction.equals(ACTION_MIDDLE_MOUSE_BUTTON)) {
                mouseWheelHorizontal = !mouseWheelHorizontal;
            }
        }
    }

    class SliderColor extends Slider {
        int value;
        float hue, sat, br, alpha;

        SliderColor(Group currentGroup, String name, float hue, float sat, float br, float alpha) {
            super(currentGroup, name);
            pushStyle();
            colorMode(HSB, 1, 1, 1, 1);
            value = color(hue, sat, br, alpha);
            this.hue = hue;
            this.sat = sat;
            this.br = br;
            this.alpha = alpha;
            popStyle();
        }

        String getState() {
            return super.getState() + hue + SEPARATOR + sat + SEPARATOR + br + SEPARATOR + alpha;
        }

        void overwriteState(String newState) {
            String[] hsba = newState.split(SEPARATOR);
            hue = Float.parseFloat(hsba[2]);
            sat = Float.parseFloat(hsba[3]);
            br = Float.parseFloat(hsba[4]);
            alpha = Float.parseFloat(hsba[5]);
        }

        boolean canHaveOverlay() {
            return true;
        }

        void updateOverlay() {

        }

    }

}
