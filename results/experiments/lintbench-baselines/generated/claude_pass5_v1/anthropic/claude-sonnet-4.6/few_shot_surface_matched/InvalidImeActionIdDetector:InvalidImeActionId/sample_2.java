package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
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
                    7,
                    Severity.ERROR,
                    new Implementation(
                            InvalidImeActionIdDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_NS_PREFIX = "android:";

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

        // Check if the value is a new ID resource reference (@+id/... or @id/...)
        // imeActionId must be an integer constant or an integer resource reference (e.g. @integer/...)
        // It must NOT be an ID resource reference.
        if (value.startsWith("@+id/") || value.startsWith("@+android:id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be set to a new ID resource (`@+id/`). "
                            + "It must be an integer constant or an integer resource reference "
                            + "(e.g. `@integer/myActionId`) as defined in `EditorInfo`.");
            return;
        }

        if (value.startsWith("@id/") || value.startsWith("@android:id/")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be set to an ID resource (`@id/`). "
                            + "It must be an integer constant or an integer resource reference "
                            + "(e.g. `@integer/myActionId`) as defined in `EditorInfo`.");
        }
    }
}