package com.winlator.widget;

import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.winlator.renderer.GLRenderer;
import com.winlator.renderer.ViewTransformation;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

final class WinDroidPointerGestures {
    private static final int NO_POINTER = -1;
    private static final float TWO_FINGER_SCROLL_STEP = 100f;
    private static final float TWO_FINGER_DRAG_DISTANCE = 0.35f;

    private final TouchpadView view;
    private final XServer xServer;
    private final Runnable fourFingerTap;
    private final float slop;
    private final SparseArray<Contact> contacts = new SparseArray<>();
    private final ViewTransformation viewport = new ViewTransformation();
    private final Runnable longPress = this::rightClickOnHold;

    private boolean direct;
    private boolean tapEligible;
    private boolean longPressHandled;
    private boolean ownsLeftButton;
    private int dragPointer = NO_POINTER;
    private int mostPointers;
    private int viewWidth;
    private int viewHeight;
    private float scrollY;

    WinDroidPointerGestures(TouchpadView view, XServer xServer, Runnable fourFingerTap) {
        this.view = view;
        this.xServer = xServer;
        this.fourFingerTap = fourFingerTap;
        slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    void setDirect(boolean direct) {
        if (this.direct == direct) return;
        cancel();
        this.direct = direct;
    }

    boolean onTouchEvent(MotionEvent event) {
        if (!view.isEnabled()) {
            cancel();
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancel();
                tapEligible = true;
                rememberContact(event, event.getActionIndex());
                if (direct) view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                view.removeCallbacks(longPress);
                updateContacts(event);
                rememberContact(event, event.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                move(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                move(event);
                contacts.remove(event.getPointerId(event.getActionIndex()));
                releaseDrag();
                break;
            case MotionEvent.ACTION_UP:
                move(event);
                finish(event.getEventTime() - event.getDownTime());
                cancel();
                break;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;
        }
        return true;
    }

    void cancel() {
        view.removeCallbacks(longPress);
        releaseDrag();
        contacts.clear();
        mostPointers = 0;
        tapEligible = false;
        longPressHandled = false;
        scrollY = 0;
    }

    private void rememberContact(MotionEvent event, int index) {
        contacts.put(event.getPointerId(index), new Contact(event.getX(index), event.getY(index)));
        mostPointers = Math.max(mostPointers, contacts.size());
        scrollY = 0;
    }

    private void updateContacts(MotionEvent event) {
        for (int index = 0; index < event.getPointerCount(); index++) {
            Contact contact = contacts.get(event.getPointerId(index));
            if (contact == null) continue;
            contact.update(event.getX(index), event.getY(index));
            if (contact.distance() > slop) tapEligible = false;
        }
        if (!tapEligible) view.removeCallbacks(longPress);
    }

    private void move(MotionEvent event) {
        if (contacts.size() == 0) return;
        boolean wasTap = tapEligible;
        updateContacts(event);
        if (longPressHandled) return;
        updateViewport();

        if (contacts.size() == 1 && !tapEligible) {
            Contact finger = contacts.valueAt(0);
            if (direct && mostPointers == 1) moveAbsolute(finger);
            else {
                boolean fromStart = wasTap && mostPointers == 1;
                // Once the hold becomes a drag, press before sending the first motion.
                if (fromStart && event.getEventTime() - event.getDownTime() >= ViewConfiguration.getLongPressTimeout()) {
                    startDrag(contacts.keyAt(0));
                }
                moveRelative(finger, fromStart);
            }
        }
        else if (contacts.size() == 2 && mostPointers == 2 && !tapEligible) {
            moveTwoFingers();
        }
    }

    private void moveTwoFingers() {
        Contact first = contacts.valueAt(0);
        Contact second = contacts.valueAt(1);
        if (ownsLeftButton) {
            moveRelative(contacts.get(dragPointer), false);
            return;
        }

        float distance = (float) Math.hypot((first.x - second.x) * scaleX(), (first.y - second.y) * scaleY());
        int screenSide = Math.min(xServer.screenInfo.width, xServer.screenInfo.height);
        if (distance < screenSide * TWO_FINGER_DRAG_DISTANCE) {
            scrollY += ((first.y - first.lastY) + (second.y - second.lastY)) * 0.5f * scaleY();
            while (Math.abs(scrollY) >= TWO_FINGER_SCROLL_STEP) {
                boolean up = scrollY > 0;
                click(up ? Pointer.Button.BUTTON_SCROLL_UP : Pointer.Button.BUTTON_SCROLL_DOWN);
                scrollY += up ? -TWO_FINGER_SCROLL_STEP : TWO_FINGER_SCROLL_STEP;
            }
        }
        else if (first.distance() <= slop || second.distance() <= slop) {
            startDrag(contacts.keyAt(first.distance() > second.distance() ? 0 : 1));
        }
    }

    private void startDrag(int pointerId) {
        if (!view.isPointerButtonLeftEnabled() || xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) return;
        dragPointer = pointerId;
        ownsLeftButton = true;
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
    }

    private void finish(long elapsed) {
        if (!tapEligible || longPressHandled || contacts.size() == 0) return;
        if (mostPointers == 1 && elapsed >= ViewConfiguration.getLongPressTimeout()) {
            rightClickOnHold();
        }
        else if (mostPointers == 1 && view.isPointerButtonLeftEnabled()) {
            if (direct) moveAbsolute(contacts.valueAt(0));
            click(Pointer.Button.BUTTON_LEFT);
        }
        else if (mostPointers == 2 && elapsed < TouchpadView.MAX_TAP_MILLISECONDS && view.isPointerButtonRightEnabled()) {
            click(Pointer.Button.BUTTON_RIGHT);
        }
        else if (mostPointers == 4 && elapsed < TouchpadView.MAX_TAP_MILLISECONDS) {
            fourFingerTap.run();
        }
    }

    private void rightClickOnHold() {
        if (!view.isEnabled() || !tapEligible || longPressHandled || mostPointers != 1 || contacts.size() != 1) return;
        longPressHandled = true;
        if (!view.isPointerButtonRightEnabled()) return;
        if (direct) moveAbsolute(contacts.valueAt(0));
        click(Pointer.Button.BUTTON_RIGHT);
    }

    private void click(Pointer.Button button) {
        if (xServer.pointer.isButtonPressed(button)) return;
        xServer.injectPointerButtonPress(button);
        xServer.injectPointerButtonRelease(button);
    }

    private void releaseDrag() {
        if (ownsLeftButton) xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
        ownsLeftButton = false;
        dragPointer = NO_POINTER;
    }

    private void updateViewport() {
        int width = Math.max(1, view.getWidth());
        int height = Math.max(1, view.getHeight());
        if (viewWidth == width && viewHeight == height) return;
        viewWidth = width;
        viewHeight = height;
        viewport.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
    }

    private float scaleX() {
        int width = xServer.getRenderer().isFullscreen() ? viewWidth : viewport.viewWidth;
        return (float) xServer.screenInfo.width / width;
    }

    private float scaleY() {
        int height = xServer.getRenderer().isFullscreen() ? viewHeight : viewport.viewHeight;
        return (float) xServer.screenInfo.height / height;
    }

    private void moveRelative(Contact finger, boolean fromStart) {
        if (finger == null) return;
        view.moveWinDroidPointerDelta((finger.x - (fromStart ? finger.startX : finger.lastX)) * scaleX(),
                (finger.y - (fromStart ? finger.startY : finger.lastY)) * scaleY());
    }

    private void moveAbsolute(Contact finger) {
        updateViewport();
        GLRenderer renderer = xServer.getRenderer();
        float x = renderer.isFullscreen() ? finger.x : finger.x - viewport.viewOffsetX;
        float y = renderer.isFullscreen() ? finger.y : finger.y - (viewHeight - viewport.viewHeight - viewport.viewOffsetY);
        int width = xServer.screenInfo.width;
        int height = xServer.screenInfo.height;
        // Undo the same pointer-centered keyboard or magnifier offset used to draw the guest.
        boolean keyboard = renderer.isScreenOffsetYRelativeToCursor();
        float zoom = keyboard ? 1f : renderer.getMagnifierZoom();
        float offsetX = clamp(xServer.pointer.getX() * zoom - width * 0.5f, width * (zoom - 1f));
        float offsetY = clamp(xServer.pointer.getY() * zoom - height * (keyboard ? 0.25f : 0.5f),
                height * (keyboard ? 0.5f : zoom - 1f));
        xServer.injectPointerMove(Math.round(clamp((x * scaleX() + offsetX) / zoom, width - 1)),
                Math.round(clamp((y * scaleY() + offsetY) / zoom, height - 1)));
    }

    private static float clamp(float value, float max) {
        return Math.max(0, Math.min(value, max));
    }

    private static final class Contact {
        final float startX;
        final float startY;
        float x;
        float y;
        float lastX;
        float lastY;

        Contact(float x, float y) {
            startX = this.x = lastX = x;
            startY = this.y = lastY = y;
        }

        void update(float x, float y) {
            lastX = this.x;
            lastY = this.y;
            this.x = x;
            this.y = y;
        }

        float distance() {
            return (float) Math.hypot(x - startX, y - startY);
        }
    }
}
