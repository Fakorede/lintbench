package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector {

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "The `android:imeActionId` attribute should be an integer constant, or "
                            + "an integer resource reference (e.g. `@integer/action_id`), "
                            + "as defined in `EditorInfo`. It should not be a resource ID such "
                            + "as `@+id/resName`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.startsWith("@")) {
            String type = getResourceType(value);
            if (!"integer".equals(type)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Use an integer constant or an integer resource reference for `android:imeActionId`");
            }
        } else if (value.startsWith("?")) {
            // Theme attribute reference (e.g. ?attr/myActionId). Allow it.
        } else {
            // Must be a valid integer constant (supporting decimal/hex up to 32 bits)
            try {
                long val = Long.decode(value);
                if (val < Integer.MIN_VALUE || val > 0xFFFFFFFFL) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Use an integer constant or an integer resource reference for `android:imeActionId`");
                }
            } catch (NumberFormatException e) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Use an integer constant or an integer resource reference for `android:imeActionId`");
            }
        }
    }

    private static String getResourceType(String value) {
        int slash = value.indexOf('/');
        if (slash == -1) {
            return null;
        }
        int start = 1; // skip '@'
        if (start < value.length() && value.charAt(start) == '+') {
            start++;
        }
        int colon = value.indexOf(':', start);
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return value.substring(start, slash);
    }
}