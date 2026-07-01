package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final String ATTR_SRC = "src";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_BITMAP = "bitmap";
    private static final String TAG_NINE_PATCH = "nine-patch";
    private static final String TAG_ITEM = "item";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private ResourceFolderType mFolderType;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_SRC);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ITEM,
                "array",
                "bool",
                "color",
                "dimen",
                "drawable",
                "fraction",
                "id",
                "integer",
                "string");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFolderType = context.getResourceFolderType();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_SRC.equals(getLocalName(attribute))) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        String tag = getLocalName(owner);
        if (!TAG_BITMAP.equals(tag) && !TAG_NINE_PATCH.equals(tag)) {
            return;
        }

        ResourceType refType = getTypeFromReference(attribute.getValue());
        if (refType != null && refType != ResourceType.DRAWABLE) {
            String message = String.format(
                    "<%1$s> aliases must reference drawable resources, not %2$s resources",
                    tag,
                    refType.getName());
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mFolderType != ResourceFolderType.VALUES) {
            return;
        }

        String tag = getLocalName(element);
        ResourceType aliasType;

        if (TAG_ITEM.equals(tag)) {
            String typeAttr = element.getAttribute(ATTR_TYPE);
            if (typeAttr == null || typeAttr.isEmpty()) {
                return;
            }
            aliasType = ResourceType.getEnum(typeAttr);
        } else {
            aliasType = ResourceType.getEnum(tag);
        }

        if (aliasType == null) {
            return;
        }

        ResourceType refType = getTypeFromReference(getTextContent(element));
        if (refType != null && refType != aliasType) {
            String message = String.format(
                    "%1$s resource aliases must reference %1$s resources, not %2$s resources",
                    aliasType.getName(),
                    refType.getName());
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String getLocalName(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon == -1 ? name : name.substring(colon + 1);
    }

    private static String getTextContent(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = child.getNodeValue();
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static ResourceType getTypeFromReference(String reference) {
        if (reference == null) {
            return null;
        }

        reference = reference.trim();
        if (reference.startsWith("?") || !reference.startsWith("@")) {
            return null;
        }

        int start = reference.indexOf('@') + 1;
        if (start >= reference.length()) {
            return null;
        }

        if (reference.charAt(start) == '*') {
            start++;
        }
        if (start < reference.length() && reference.charAt(start) == '+') {
            start++;
        }

        int colon = reference.indexOf(':', start);
        if (colon != -1) {
            start = colon + 1;
        }

        int slash = reference.indexOf('/', start);
        if (slash <= start) {
            return null;
        }

        String typeName = reference.substring(start, slash);
        return ResourceType.getEnum(typeName);
    }
}