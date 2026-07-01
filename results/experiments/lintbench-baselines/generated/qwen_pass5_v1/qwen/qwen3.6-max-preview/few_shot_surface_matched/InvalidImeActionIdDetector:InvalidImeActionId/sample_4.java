package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
                    "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class,
                            Scope.RESOURCE_XML_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue().trim();
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "android:imeActionId must be an integer constant or an integer resource reference, not an ID resource");
        }
    }
}