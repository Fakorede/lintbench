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
                    "android:imeActionId should not be a resource ID such as `@+id/resName`. " +
                    "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
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
        if (value == null) {
            return;
        }
        value = value.trim();
        if (isIdResource(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "android:imeActionId should not be a resource ID such as `@+id/resName`. It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.");
        }
    }

    private boolean isIdResource(String value) {
        if (!value.startsWith("@")) {
            return false;
        }
        int slash = value.indexOf('/');
        if (slash == -1) {
            return false;
        }
        int colon = value.indexOf(':');
        int start = 1;
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        String type = value.substring(start, slash);
        return "id".equals(type) || "+id".equals(type);
    }
}