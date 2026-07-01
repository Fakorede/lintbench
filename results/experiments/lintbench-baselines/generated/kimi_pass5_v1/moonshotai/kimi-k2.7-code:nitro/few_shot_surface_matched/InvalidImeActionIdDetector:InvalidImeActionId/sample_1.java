package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId Declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isIdResourceReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Invalid `android:imeActionId`: must be an integer constant or an integer "
                            + "resource reference, not an ID resource reference");
        }
    }

    private static boolean isIdResourceReference(String value) {
        if (!value.startsWith("@")) {
            return false;
        }

        int typeStart = value.startsWith("@+") ? 2 : 1;
        int slash = value.indexOf('/', typeStart);
        if (slash == -1) {
            return false;
        }

        int colon = value.indexOf(':', typeStart);
        String type;
        if (colon != -1 && colon < slash) {
            type = value.substring(colon + 1, slash);
        } else {
            type = value.substring(typeStart, slash);
        }

        return "id".equals(type);
    }
}