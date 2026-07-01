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
 * Detector that checks for incorrect reference types in resource aliases.
 * When you create a resource alias (e.g., @color/foo pointing to @drawable/bar),
 * the referenced resource must be of the same type as the alias.
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

    public DuplicateResourceDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "color",
                "dimen",
                "string",
                "bool",
                "integer",
                "drawable",
                "style",
                "array",
                "string-array",
                "integer-array",
                "fraction",
                "id",
                "plurals",
                "attr"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Get the text content of the element
        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        // Check if the value is a resource reference
        if (!text.startsWith("@")) {
            return;
        }

        // Parse the reference type
        // Format: @type/name or @+type/name or @android:type/name
        String reference = text;
        if (reference.startsWith("@+")) {
            reference = reference.substring(2);
        } else {
            reference = reference.substring(1);
        }

        // Remove package prefix if present (e.g., "android:")
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Split into type and name
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Normalize the element tag to a resource type
        String aliasType = normalizeType(tagName);
        String normalizedReferencedType = normalizeType(referencedType);

        if (aliasType == null || normalizedReferencedType == null) {
            return;
        }

        if (!aliasType.equals(normalizedReferencedType)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Wrong resource type: expected value of type `@%1$s` but got `@%2$s`",
                            aliasType,
                            normalizedReferencedType
                    )
            );
        }
    }

    /**
     * Normalizes a resource type tag name to a canonical resource type string.
     */
    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case "color":
                return "color";
            case "dimen":
            case "fraction":
                return "dimen";
            case "string":
                return "string";
            case "bool":
                return "bool";
            case "integer":
                return "integer";
            case "drawable":
                return "drawable";
            case "style":
                return "style";
            case "array":
            case "string-array":
            case "integer-array":
                return "array";
            case "id":
                return "id";
            case "plurals":
                return "plurals";
            case "attr":
                return "attr";
            default:
                return type;
        }
    }
}