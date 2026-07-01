package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class InvalidImeActionIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_IME_ACTION_ID = "imeActionId";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` must be an integer constant or an integer resource "
                            + "reference (e.g. `@integer/action_id`). It must not be a widget ID "
                            + "reference such as `@+id/resName`, because "
                            + "`EditorInfo.imeActionId` expects an integer value.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        if (isWidgetIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid value for `android:imeActionId`: must be an integer constant or an "
                            + "integer resource reference, not a widget ID reference.");
        }
    }

    private static boolean isWidgetIdReference(String value) {
        String v = value.trim();
        if (!v.startsWith("@")) {
            return false;
        }

        int start = 1;
        int len = v.length();
        if (start < len && v.charAt(start) == '*') {
            start++;
        }

        int colon = v.indexOf(':', start);
        int slash = v.indexOf('/', start);
        if (slash == -1) {
            return false;
        }

        int typeStart = colon == -1 ? start : colon + 1;
        if (typeStart >= slash) {
            return false;
        }

        String type = v.substring(typeStart, slash);
        return "id".equals(type) || "+id".equals(type);
    }
}