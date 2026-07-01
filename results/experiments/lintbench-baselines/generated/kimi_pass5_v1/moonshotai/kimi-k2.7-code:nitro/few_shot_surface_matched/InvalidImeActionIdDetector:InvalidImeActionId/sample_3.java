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
                    "Invalid imeActionId declaration",
                    "The `android:imeActionId` attribute should be an integer constant or an "
                            + "integer resource reference, as defined in `EditorInfo`. Using an id "
                            + "reference such as `@+id/resName` is invalid and will not work as "
                            + "expected.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_IME_ACTION_ID = "imeActionId";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` must be an integer constant or an integer resource "
                            + "reference, not an id reference");
        }
    }

    private static boolean isIdReference(String value) {
        if (!value.startsWith("@")) {
            return false;
        }

        int colonIndex = value.indexOf(':');
        int slashIndex = value.indexOf('/');
        if (slashIndex == -1 || (colonIndex != -1 && colonIndex > slashIndex)) {
            return false;
        }

        String type = value.substring(colonIndex != -1 ? colonIndex + 1 : 1, slashIndex);
        return type.equals("id") || type.equals("+id");
    }
}