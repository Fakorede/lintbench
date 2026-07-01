package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("imeActionId");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        boolean isValid = true;
        if (value.startsWith("@")) {
            // Must be an integer resource reference, e.g., @integer/name or @android:integer/name
            if (!value.startsWith("@integer/") && !value.startsWith("@android:integer/")) {
                isValid = false;
            }
        } else if (value.startsWith("?")) {
            // Theme attributes can resolve to integers, so we allow them
            isValid = true;
        } else {
            // Must be a valid integer constant (decimal, hex, etc.)
            try {
                Integer.decode(value);
            } catch (NumberFormatException e) {
                isValid = false;
            }
        }

        if (!isValid) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "`android:imeActionId` must be an integer constant or an integer resource reference"
            );
        }
    }
}