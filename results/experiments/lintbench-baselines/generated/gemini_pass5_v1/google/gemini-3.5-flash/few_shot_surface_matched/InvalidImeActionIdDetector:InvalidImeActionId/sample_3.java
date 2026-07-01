package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class, Scope.LAYOUT_RESOURCE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.startsWith("@")) {
            boolean isValidIntegerRef = false;
            int slash = value.indexOf('/');
            if (slash != -1) {
                String prefix = value.substring(1, slash);
                if (prefix.startsWith("+")) {
                    prefix = prefix.substring(1);
                }
                int colon = prefix.indexOf(':');
                String type = colon != -1 ? prefix.substring(colon + 1) : prefix;
                if ("integer".equals(type)) {
                    isValidIntegerRef = true;
                }
            }
            if (!isValidIntegerRef) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "`android:imeActionId` should be an integer constant, or an integer resource reference");
            }
        } else {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "`android:imeActionId` should be an integer constant, or an integer resource reference");
            }
        }
    }
}