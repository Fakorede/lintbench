package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
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
 * reference (e.g., {@code @integer/my_action}). It must NOT be an ID resource reference such as
 * {@code @+id/resName} or {@code @id/resName}.
 */
public class InvalidImeActionIdDetector extends ResourceXmlDetector {

    /** The main issue reported by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link InvalidImeActionIdDetector} */
    public InvalidImeActionIdDetector() {}

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only check the android:imeActionId attribute
        String namespaceUri = attribute.getNamespaceURI();
        if (namespaceUri != null && !namespaceUri.equals(ANDROID_NS)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isIdResourceReference(value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should not be set to an ID resource reference (`"
                            + value
                            + "`). It must be an integer constant or an integer resource "
                            + "reference (e.g., `@integer/action_id`).");
        }
    }

    /**
     * Returns true if the given attribute value is an ID resource reference,
     * i.e., starts with {@code @+id/} or {@code @id/}.
     */
    private static boolean isIdResourceReference(@NonNull String value) {
        // Check for @+id/name or @id/name patterns
        if (value.startsWith("@")) {
            // Strip the leading '@'
            String withoutAt = value.substring(1);

            // Handle @+id/name
            if (withoutAt.startsWith("+")) {
                withoutAt = withoutAt.substring(1);
            }

            // Check if the resource type is 'id'
            int slashIndex = withoutAt.indexOf('/');
            if (slashIndex > 0) {
                String resourceTypePart = withoutAt.substring(0, slashIndex);

                // Handle package-qualified references like @android:id/name
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