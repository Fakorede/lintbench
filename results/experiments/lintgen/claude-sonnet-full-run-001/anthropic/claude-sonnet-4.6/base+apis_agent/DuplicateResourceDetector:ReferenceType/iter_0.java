package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for incorrect resource alias reference types.
 *
 * When you create a resource alias (e.g. a drawable that points to another drawable,
 * or a layout alias), the referenced resource must be of the same type as the alias.
 */
public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final String ATTR_TYPE = "type";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    public DuplicateResourceDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're looking for items that are resource aliases, e.g.:
        // <item type="drawable" name="foo">@drawable/bar</item>
        // The referenced resource type must match the declared type.

        String declaredType = element.getAttribute(ATTR_TYPE);
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element (the reference value)
        String value = getTextContent(element);
        if (value == null || value.isEmpty()) {
            return;
        }

        value = value.trim();

        // Check if the value is a resource reference
        if (!value.startsWith("@")) {
            return;
        }

        // Parse the reference: @[package:]type/name
        // Strip leading @
        String reference = value.substring(1);

        // Handle @+id/ style references - skip those
        if (reference.startsWith("+")) {
            return;
        }

        // Strip package prefix if present (e.g. "android:drawable/foo" -> "drawable/foo")
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);
        if (referencedType.isEmpty()) {
            return;
        }

        // Normalize both types for comparison
        // Some types have aliases (e.g. "color" can reference "color")
        if (!typesMatch(declaredType, referencedType)) {
            String name = element.getAttribute(ATTR_NAME);
            String message = String.format(
                    "Wrong type for resource alias `%1$s`: expected `%2$s`, got `%3$s`",
                    name, declaredType, referencedType);

            // Try to find the location of the text node for better error reporting
            Node textNode = getTextNode(element);
            if (textNode != null) {
                context.report(ISSUE, element, context.getLocation(textNode), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    /**
     * Returns true if the declared type and referenced type are compatible.
     */
    private static boolean typesMatch(@NonNull String declaredType,
            @NonNull String referencedType) {
        if (declaredType.equals(referencedType)) {
            return true;
        }

        // Normalize resource types using ResourceType if possible
        ResourceType declared = ResourceType.fromXmlValue(declaredType);
        ResourceType referenced = ResourceType.fromXmlValue(referencedType);

        if (declared != null && referenced != null) {
            return declared == referenced;
        }

        // Fallback: case-insensitive comparison
        return declaredType.equalsIgnoreCase(referencedType);
    }

    /**
     * Gets the trimmed text content of an element.
     */
    @Nullable
    private static String getTextContent(@NonNull Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE ||
                    child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Gets the first text node child of an element.
     */
    @Nullable
    private static Node getTextNode(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String value = child.getNodeValue();
                if (value != null && !value.trim().isEmpty()) {
                    return child;
                }
            }
        }
        return null;
    }
}