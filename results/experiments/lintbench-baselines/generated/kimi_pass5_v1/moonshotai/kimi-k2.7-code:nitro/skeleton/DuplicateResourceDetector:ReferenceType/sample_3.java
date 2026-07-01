package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_ITEM = "item";
    private static final String TAG_RESOURCES = "resources";

    private static final Set<String> ALIASABLE_VALUE_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "drawable",
                            "color",
                            "dimen",
                            "string",
                            "integer",
                            "bool",
                            "fraction",
                            "id"));

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you create an alias to an existing resource, the reference you supply "
                            + "must point to a resource of the same type. For example, an "
                            + "`<item type=\\\"drawable\\\">` alias must reference a `@drawable/...` resource.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_NAME, ATTR_TYPE);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to initialize per file.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Node parent = element.getParentNode();
        if (parent == null || !TAG_RESOURCES.equals(parent.getNodeName())) {
            return;
        }

        String aliasType = getExpectedType(attribute);
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String reference = element.getTextContent().trim();
        if (reference.isEmpty()) {
            return;
        }

        String referencedType = getReferencedType(reference);
        if (referencedType == null) {
            return;
        }

        if (!aliasType.equals(referencedType)) {
            String message =
                    String.format(
                            "Expected reference of type '%1$s' but was '%2$s'",
                            aliasType, referencedType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String getExpectedType(@NonNull Attr attribute) {
        String attrName = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();

        if (ATTR_TYPE.equals(attrName) && TAG_ITEM.equals(tag)) {
            return attribute.getValue();
        }

        if (ATTR_NAME.equals(attrName)
                && !TAG_ITEM.equals(tag)
                && ALIASABLE_VALUE_TAGS.contains(tag)) {
            return tag;
        }

        return null;
    }

    private static String getReferencedType(@NonNull String reference) {
        if (!reference.startsWith("@")) {
            return null;
        }

        String s = reference.substring(1);
        if (s.startsWith("+")) {
            s = s.substring(1);
        }

        int slash = s.indexOf('/');
        if (slash <= 0) {
            return null;
        }

        int colon = s.lastIndexOf(':', slash);
        int start = colon != -1 ? colon + 1 : 0;
        String type = s.substring(start, slash);
        return type.isEmpty() ? null : type;
    }
}