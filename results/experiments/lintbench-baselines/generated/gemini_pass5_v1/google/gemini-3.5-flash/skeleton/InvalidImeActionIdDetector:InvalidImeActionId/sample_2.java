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

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        boolean isValid = true;
        if (value.startsWith("@")) {
            if (value.startsWith("@id/") || value.startsWith("@+id/") || value.startsWith("@android:id/")) {
                isValid = false;
            } else if (!value.startsWith("@integer/") && !value.startsWith("@android:integer/")) {
                isValid = false;
            }
        } else if (value.startsWith("?")) {
            isValid = true;
        } else {
            try {
                Long.decode(value);
            } catch (NumberFormatException e) {
                isValid = false;
            }
        }

        if (!isValid) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "`android:imeActionId` should be an integer constant or an integer resource reference.");
        }
    }
}