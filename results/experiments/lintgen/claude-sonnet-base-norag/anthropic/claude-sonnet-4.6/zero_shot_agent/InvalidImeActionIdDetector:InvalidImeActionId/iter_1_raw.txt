package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
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

/**
 * Detector that checks for invalid {@code android:imeActionId} attribute values.
 *
 * <p>The {@code android:imeActionId} attribute must be an integer constant or an integer resource
 * reference (e.g. {@code @integer/my_action}), NOT a view ID reference such as
 * {@code @+id/resName} or {@code @id/resName}.
 */
public class InvalidImeActionIdDetector extends LayoutDetector {

    /** The main issue reported by this detector. */
    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link InvalidImeActionIdDetector}. */
    public InvalidImeActionIdDetector() {
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about android:imeActionId
        String namespaceUri = attribute.getNamespaceURI();
        if (namespaceUri != null && !namespaceUri.equals(ANDROID_NS)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isIdReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "`android:imeActionId` should not be set to a resource ID (`"
                            + value
                            + "`). It must be an integer constant or an integer resource "
                            + "reference (e.g. `@integer/action_id`).");
        }
    }

    /**
     * Returns {@code true} if the given attribute value looks like a view ID reference,
     * i.e. starts with {@code @id/} or {@code @+id/}.
     */
    private static boolean isIdReference(@NonNull String value) {
        // Match @+id/... or @id/...
        if (value.startsWith("@")) {
            // Strip leading '@'
            String withoutAt = value.substring(1);

            // Strip optional '+' (for new-id declarations)
            if (withoutAt.startsWith("+")) {
                withoutAt = withoutAt.substring(1);
            }

            // Check if the resource type is "id"
            int slashIndex = withoutAt.indexOf('/');
            if (slashIndex > 0) {
                String resourceTypePart = withoutAt.substring(0, slashIndex);
                // Handle optional package prefix (e.g. @android:id/...)
                int colonIndex = resourceTypePart.indexOf(':');
                if (colonIndex >= 0) {
                    resourceTypePart = resourceTypePart.substring(colonIndex + 1);
                }
                return ResourceType.ID.getName().equals(resourceTypePart);
            }
        }
        return false;
    }
}