package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class InvalidImeActionIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId",
                    "`android:imeActionId` must be set to an integer constant or an integer "
                            + "resource reference. A resource ID reference such as "
                            + "`@+id/resName` is not valid because `EditorInfo#imeActionId` "
                            + "expects an integer value.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value != null && isIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Invalid `android:imeActionId`: it must be an integer constant or an "
                            + "integer resource reference, not a resource ID");
        }
    }

    private static boolean isIdReference(String value) {
        if (!value.startsWith("@")) {
            return false;
        }
        int slash = value.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        int typeStart = 1;
        if (value.charAt(typeStart) == '+') {
            typeStart++;
        }
        int colon = value.indexOf(':', typeStart);
        if (colon >= 0 && colon < slash) {
            typeStart = colon + 1;
        }
        return slash > typeStart && "id".equals(value.substring(typeStart, slash));
    }
}