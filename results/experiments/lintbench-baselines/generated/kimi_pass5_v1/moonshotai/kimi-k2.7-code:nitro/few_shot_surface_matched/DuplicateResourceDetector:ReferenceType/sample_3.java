package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    public static final Issue REFERENCE_TYPE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be"
                            + " of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private ResourceFolderType mFolderType;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mFolderType = context.getResourceFolderType();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mFolderType != ResourceFolderType.VALUES) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        ResourceType expected = getResourceType(owner);
        if (expected == null) {
            return;
        }

        if (ATTR_NAME.equals(attribute.getName())) {
            String text = getTextContent(owner).trim();
            if (!text.isEmpty()) {
                ResourceType referenced = getReferenceType(text);
                if (referenced != null && referenced != expected) {
                    String message =
                            "The resource alias is defined as type \""
                                    + expected.getName()
                                    + "\" but references a resource of type \""
                                    + referenced.getName()
                                    + "\"";
                    context.report(
                            REFERENCE_TYPE,
                            owner,
                            context.getLocation(owner),
                            message);
                }
            }
        }

        String value = attribute.getValue();
        if (!value.isEmpty() && (value.startsWith("@") || value.startsWith("?"))) {
            ResourceType referenced = getReferenceType(value);
            if (referenced != null && referenced != expected) {
                String message =
                        "The resource alias is defined as type \""
                                + expected.getName()
                                + "\" but references a resource of type \""
                                + referenced.getName()
                                + "\"";
                context.report(
                        REFERENCE_TYPE,
                        attribute,
                        context.getLocation(attribute),
                        message);
            }
        }
    }

    private static ResourceType getResourceType(Element element) {
        String tag = element.getTagName();
        if (TAG_ITEM.equals(tag)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (!type.isEmpty()) {
                return ResourceType.getEnum(type);
            }
        }
        return ResourceType.getEnum(tag);
    }

    private static ResourceType getReferenceType(String ref) {
        int start = 0;
        if (ref.startsWith("@+")) {
            start = 2;
        } else if (ref.startsWith("@") || ref.startsWith("?")) {
            start = 1;
        }

        int slash = ref.indexOf('/', start);
        if (slash == -1) {
            return null;
        }

        String type = ref.substring(start, slash);
        int colon = type.indexOf(':');
        if (colon != -1) {
            type = type.substring(colon + 1);
        }

        return ResourceType.getEnum(type);
    }

    private static String getTextContent(Element element) {
        StringBuilder sb = new StringBuilder();
        Node child = element.getFirstChild();
        while (child != null) {
            short nodeType = child.getNodeType();
            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
            child = child.getNextSibling();
        }
        return sb.toString();
    }
}