package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_TYPE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference type",
            "When you generate a resource alias, the resource you are pointing to must be "
                    + "of the same type as the alias.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceType aliasType = getAliasType(element);
        if (aliasType == null) {
            return;
        }

        String reference = getReferenceText(element);
        if (reference == null) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(reference);
        if (url == null || url.type == null) {
            return;
        }

        if (url.type != aliasType) {
            String message = String.format(
                    "Resource alias of type `%1$s` references a resource of type `%2$s`",
                    aliasType.getName(),
                    url.type.getName());
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static ResourceType getAliasType(@NonNull Element element) {
        String tag = element.getTagName();

        if ("item".equals(tag)) {
            String typeAttr = element.getAttribute(ATTR_TYPE);
            if (typeAttr.isEmpty()) {
                return null;
            }
            return ResourceType.fromXmlTag(typeAttr);
        }

        ResourceType type = ResourceType.fromXmlTag(tag);
        // <attr> elements can contain default value references; they are not aliases.
        if (type == ResourceType.ATTR) {
            return null;
        }
        return type;
    }

    @Nullable
    private static String getReferenceText(@NonNull Element element) {
        Node child = element.getFirstChild();
        if (child == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        while (child != null) {
            short nodeType = child.getNodeType();
            if (nodeType == Node.ELEMENT_NODE) {
                // Nested elements mean this is not a simple alias.
                return null;
            }
            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
            child = child.getNextSibling();
        }

        String text = sb.toString().trim();
        if (text.isEmpty() || !text.startsWith("@")) {
            return null;
        }
        return text;
    }
}