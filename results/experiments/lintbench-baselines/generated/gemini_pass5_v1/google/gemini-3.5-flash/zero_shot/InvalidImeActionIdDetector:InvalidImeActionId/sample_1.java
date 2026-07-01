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
            Severity.ERROR,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
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

        // Ignore data binding expressions or theme attributes
        if (value.startsWith("@{") || value.startsWith("?")) {
            return;
        }

        boolean isValid = false;
        if (value.startsWith("@")) {
            if (value.startsWith("@integer/") || value.startsWith("@android:integer/")) {
                isValid = true;
            }
        } else {
            try {
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    Long.parseLong(value.substring(2), 16);
                } else if (value.startsWith("-0x") || value.startsWith("-0X")) {
                    Long.parseLong("-" + value.substring(3), 16);
                } else {
                    Long.parseLong(value);
                }
                isValid = true;
            } catch (NumberFormatException e) {
                isValid = false;
            }
        }

        if (!isValid) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` must be an integer constant or an integer resource reference"
            );
        }
    }
}