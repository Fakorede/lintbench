package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceUrl;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;

public class InvalidImeActionIdDetector extends Detector implements Detector.XmlScanner {

    private static final String EXPLANATION =
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.";

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            EXPLANATION,
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String trimmed = value.trim();

        if (trimmed.startsWith("@{") || trimmed.startsWith("@=")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(trimmed);
        if (url != null) {
            if (url.type == ResourceType.INTEGER) {
                return;
            }
            reportIssue(context, attribute);
            return;
        }

        try {
            Integer.decode(trimmed);
        } catch (NumberFormatException e) {
            reportIssue(context, attribute);
        }
    }

    private static void reportIssue(@NonNull XmlContext context, @NonNull Attr attribute) {
        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Invalid `android:imeActionId`: must be an integer constant or an integer resource reference"
        );
    }
}