package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks that resource aliases point to resources of the same type.
 */
public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias.",
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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're looking for <item type="X" name="Y">@Z/name</item>
        // where Z != X (mismatched resource type in alias)

        String typeAttr = element.getAttribute(ATTR_TYPE);
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Get the text content of the element
        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        // Check if it's a resource reference
        if (!text.startsWith("@")) {
            return;
        }

        // Strip leading @
        String reference = text.substring(1);

        // Handle @+type/name (creation) - strip the +
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Now reference should be "type/name" or "package:type/name"
        // Strip package prefix if present
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Normalize both types for comparison
        // Some types have aliases (e.g., "integer" vs "int")
        if (!typeAttr.equals(referencedType)) {
            // Check if they are equivalent types
            if (!areEquivalentTypes(typeAttr, referencedType)) {
                String name = element.getAttribute(ATTR_NAME);
                Attr typeNode = element.getAttributeNode(ATTR_TYPE);

                String message = String.format(
                        "Wrong type for resource alias `%1$s`; expected `%2$s` but was `%3$s`",
                        name, typeAttr, referencedType);

                if (typeNode != null) {
                    context.report(ISSUE, element, context.getLocation(typeNode), message);
                } else {
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }

    /**
     * Checks whether two resource type strings are equivalent (e.g. some types
     * have multiple names).
     */
    private static boolean areEquivalentTypes(@NonNull String type1, @NonNull String type2) {
        if (type1.equals(type2)) {
            return true;
        }

        // Normalize using ResourceType if possible
        ResourceType rt1 = ResourceType.fromXmlValue(type1);
        ResourceType rt2 = ResourceType.fromXmlValue(type2);

        if (rt1 != null && rt2 != null) {
            return rt1 == rt2;
        }

        return false;
    }
}