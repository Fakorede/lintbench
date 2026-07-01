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
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class,
                            Scope.LAYOUT_RESOURCE_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@id/")
                || value.startsWith("@+id/")
                || value.startsWith("@android:id/")
                || value.startsWith("@+android:id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be an id resource; use an integer instead");
        }
    }
}