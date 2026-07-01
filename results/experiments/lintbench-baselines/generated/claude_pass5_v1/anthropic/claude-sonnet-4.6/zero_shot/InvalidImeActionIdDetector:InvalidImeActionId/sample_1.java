package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
 * reference (e.g. {@code @integer/foo}), not an ID resource reference (e.g. {@code @+id/foo} or
 * {@code @id/foo}).
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
                    6,
                    Severity.WARNING,
                    new Implementation(
                            InvalidImeActionIdDetector.class,
                            Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    private static final String ATTR_IME_ACTION_ID = "imeActionId";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link InvalidImeActionIdDetector}. */
    public InvalidImeActionIdDetector() {
    }

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
                    "`android:imeActionId` should not be set to an ID resource reference (`"
                            + value
                            + "`). It must be an integer constant or an integer resource "
                            + "reference (e.g. `@integer/foo`).");
        }
    }

    /**
     * Returns {@code true} if the given attribute value looks like an ID resource reference,
     * i.e. it matches {@code @+id/...} or {@code @id/...}.
     */
    private static boolean isIdReference(@NonNull String value) {
        if (value.startsWith("@+id/") || value.startsWith("@+android:id/")) {
            return true;
        }
        if (value.startsWith("@id/") || value.startsWith("@android:id/")) {
            return true;
        }
        // Also handle the case where the resource type token is "id" after stripping @
        // to be robust against unusual spacing or capitalisation is not expected, but
        // let's also guard against "@ID/" style.
        if (value.startsWith("@")) {
            // Strip leading '@' and optional '+'
            String rest = value.substring(1);
            if (rest.startsWith("+")) {
                rest = rest.substring(1);
            }
            // Strip optional package prefix (e.g. "android:")
            int colonIndex = rest.indexOf(':');
            if (colonIndex >= 0) {
                rest = rest.substring(colonIndex + 1);
            }
            // Now rest should start with "<type>/"
            int slashIndex = rest.indexOf('/');
            if (slashIndex > 0) {
                String typeName = rest.substring(0, slashIndex);
                if (ResourceType.ID.getName().equalsIgnoreCase(typeName)) {
                    return true;
                }
            }
        }
        return false;
    }
}