package android.view;

import android.os.Looper;

/** Compile-only signature used to compile a real InputEventReceiver subclass. */
public abstract class InputEventReceiver {
    public InputEventReceiver(InputChannel inputChannel, Looper looper) {
        throw new UnsupportedOperationException("Compile-only Android platform stub");
    }

    public void onInputEvent(InputEvent event) {
        throw new UnsupportedOperationException("Compile-only Android platform stub");
    }

    public final void finishInputEvent(InputEvent event, boolean handled) {
        throw new UnsupportedOperationException("Compile-only Android platform stub");
    }

    public void dispose() {
        throw new UnsupportedOperationException("Compile-only Android platform stub");
    }
}
