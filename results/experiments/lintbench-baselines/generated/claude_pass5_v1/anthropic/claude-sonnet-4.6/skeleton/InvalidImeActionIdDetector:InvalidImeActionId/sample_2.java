package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    private static final String ATTR_IME_ACTION_ID = "imeActionId";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Check if the value is a new ID resource reference (@+id/...)
        // This is invalid for imeActionId - it must be an integer constant or integer resource
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be a resource ID (`"
                            + value
                            + "`). It must be an integer constant or an integer resource "
                            + "reference (such as `@integer/...`), as defined in `EditorInfo`.");
        }
    }
}