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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` corresponds to `EditorInfo.actionId` and must be an "
                            + "integer constant or an `@integer/...` resource reference. It should "
                            + "not be a resource ID such as `@+id/resName` or `@id/resName`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        if (value.startsWith("@")) {
            if (isIntegerResourceReference(value)) {
                return;
            }
        } else if (isIntegerConstant(value)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Invalid `android:imeActionId` value: must be an integer constant or an "
                        + "`@integer/...` resource reference, not a resource ID such as "
                        + "`@+id/...`");
    }

    private static boolean isIntegerResourceReference(String value) {
        int slash = value.indexOf('/');
        if (slash == -1) {
            return false;
        }

        String type = value.substring(1, slash);
        if (type.startsWith("+")) {
            type = type.substring(1);
        } else if (type.startsWith("*")) {
            type = type.substring(1);
        }

        int colon = type.indexOf(':');
        if (colon != -1) {
            type = type.substring(colon + 1);
        }

        return "integer".equals(type);
    }

    private static boolean isIntegerConstant(String value) {
        try {
            Integer.decode(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}