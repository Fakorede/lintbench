package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScannerConstants;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class DuplicateResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue REFERENCE_TYPE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you create a resource alias, the referenced resource must be of the same type as the alias. "
                    + "For example, a drawable alias must point to a @drawable resource, not a @mipmap or @color resource.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !(parent instanceof Element)
                || !"resources".equals(((Element) parent).getTagName())) {
            return;
        }

        String value = getReferenceValue(element);
        if (value == null) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || !url.isReference()) {
            return;
        }

        ResourceType aliasType = getAliasType(element);
        if (aliasType == null) {
            return;
        }

        ResourceType referencedType = url.getType();
        if (referencedType != null && referencedType != aliasType) {
            String message = String.format(
                    "Alias of type %s points to a %s resource (`%s`); the reference must be of the same type as the alias",
                    aliasType.getName(), referencedType.getName(), value);
            context.report(REFERENCE_TYPE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static String getReferenceValue(@NonNull Element element) {
        Node child = element.getFirstChild();
        if (child == null
                || child.getNodeType() != Node.TEXT_NODE
                || child.getNextSibling() != null) {
            return null;
        }

        String text = child.getNodeValue().trim();
        return text.isEmpty() ? null : text;
    }

    @Nullable
    private static ResourceType getAliasType(@NonNull Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return ResourceType.getEnum(type);
            }
            return null;
        }

        return ResourceType.getEnum(tag);
    }
}