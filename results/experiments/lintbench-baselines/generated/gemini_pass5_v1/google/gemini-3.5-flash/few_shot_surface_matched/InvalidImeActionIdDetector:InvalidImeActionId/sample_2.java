package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends LayoutDetector {

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
                            InvalidImeActionIdDetector.class, Scope.LAYOUT_SCOPE));

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

        if (value.startsWith("@+id/") || value.startsWith("@id/") || value.startsWith("@android:id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be a resource ID; use an integer constant or an integer resource instead.");
        } else if (value.startsWith("@") && !value.startsWith("@integer/") && !value.startsWith("@android:integer/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` must be an integer constant or an integer resource reference.");
        } else if (!value.startsWith("@") && !value.startsWith("?")) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "`android:imeActionId` must be an integer constant or an integer resource reference.");
            }
        }
    }
}