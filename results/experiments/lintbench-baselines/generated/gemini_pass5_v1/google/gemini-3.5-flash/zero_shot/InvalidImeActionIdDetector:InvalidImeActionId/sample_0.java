package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
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
            "It must be an integer constant, or an integer resource reference, " +
            "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
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

        // Skip databinding expressions
        if (value.startsWith("@{") || value.startsWith("@={")) {
            return;
        }

        if (value.startsWith("@")) {
            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.type != ResourceType.INTEGER) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "android:imeActionId should be an integer resource reference (e.g. `@integer/action_id`) or an integer constant"
                );
            }
        } else if (value.startsWith("?")) {
            // Theme attribute reference - assume it resolves to a valid integer
        } else {
            // Must be an integer constant
            try {
                Integer.decode(value);
            } catch (NumberFormatException e) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "android:imeActionId must be an integer constant"
                );
            }
        }
    }
}