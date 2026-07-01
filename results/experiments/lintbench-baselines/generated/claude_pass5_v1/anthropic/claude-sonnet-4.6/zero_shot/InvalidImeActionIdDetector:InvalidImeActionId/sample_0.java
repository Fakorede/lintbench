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
 * reference (e.g. {@code @integer/my_action_id}), NOT a generic resource ID such as
 * {@code @+id/resName} or {@code @id/resName}.
 */
public class InvalidImeActionIdDetector extends ResourceXmlDetector {

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
        // Only care about the android:imeActionId attribute
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
                    "`android:imeActionId` should not be set to a resource ID (`" + value + "`). "
                            + "It must be an integer constant or an `@integer` resource reference.");
        }
    }

    /**
     * Returns {@code true} if the given attribute value looks like an ID resource reference,
     * i.e. it starts with {@code @+id/} or {@code @id/} (with an optional package prefix).
     */
    private static boolean isIdResourceReference(@NonNull String value) {
        // Strip leading whitespace just in case
        String trimmed = value.trim();

        // Handle @+id/... (new ID declaration)
        if (trimmed.startsWith("@+id/") || trimmed.startsWith("@+android:id/")) {
            return true;
        }

        // Handle @id/... or @android:id/... (plain ID reference)
        if (trimmed.startsWith("@id/") || trimmed.startsWith("@android:id/")) {
            return true;
        }

        // Handle package-qualified forms like @com.example:id/foo
        if (trimmed.startsWith("@")) {
            // Find the resource type portion
            int slashIndex = trimmed.indexOf('/');
            if (slashIndex > 0) {
                String typeAndPackage = trimmed.substring(1, slashIndex);
                // typeAndPackage could be "id", "package:id", "+id", "+package:id"
                // Strip leading '+'
                if (typeAndPackage.startsWith("+")) {
                    typeAndPackage = typeAndPackage.substring(1);
                }
                // Strip package prefix if present
                int colonIndex = typeAndPackage.indexOf(':');
                String resourceType;
                if (colonIndex >= 0) {
                    resourceType = typeAndPackage.substring(colonIndex + 1);
                } else {
                    resourceType = typeAndPackage;
                }
                if (ResourceType.ID.getName().equals(resourceType)) {
                    return true;
                }
            }
        }

        return false;
    }
}