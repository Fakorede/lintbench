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
 * reference, not a layout ID reference (e.g., {@code @+id/resName} or {@code @id/resName}).
 */
public class InvalidImeActionIdDetector extends ResourceXmlDetector {

    /** The main issue reported by this detector. */
    public static final Issue ISSUE =
            Issue.create(
                    "InvalidImeActionId",
                    "Invalid imeActionId declaration",
                    "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                            + "It must be an integer constant, or an integer resource reference, "
                            + "as defined in `EditorInfo`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link InvalidImeActionIdDetector}. */
    public InvalidImeActionIdDetector() {}

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
                    "`android:imeActionId` should not be a resource ID reference (`"
                            + value
                            + "`). It must be an integer constant or an integer resource "
                            + "reference (e.g., `@integer/myActionId`).");
        }
    }

    /**
     * Returns {@code true} if the given attribute value looks like an ID resource reference,
     * i.e., starts with {@code @id/} or {@code @+id/}.
     */
    private static boolean isIdReference(@NonNull String value) {
        // Match @+id/... or @id/...
        if (value.startsWith("@")) {
            // Strip leading '@'
            String remainder = value.substring(1);
            // Handle new-id declaration: @+id/...
            if (remainder.startsWith("+")) {
                remainder = remainder.substring(1);
            }
            // Now check if the resource type is "id"
            int slashIndex = remainder.indexOf('/');
            if (slashIndex != -1) {
                String resourceTypeName = remainder.substring(0, slashIndex);
                // Strip any package prefix (e.g., "android:id" -> "id")
                int colonIndex = resourceTypeName.indexOf(':');
                if (colonIndex != -1) {
                    resourceTypeName = resourceTypeName.substring(colonIndex + 1);
                }
                return ResourceType.ID.getName().equals(resourceTypeName);
            }
        }
        return false;
    }
}