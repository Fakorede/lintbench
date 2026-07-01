package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Locale;

public class DuplicateResourceDetector extends Detector implements Detector.XmlScanner {

    public static final Issue REFERENCE_TYPE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias. "
                    + "For example, a `<drawable>` alias resource must point to a `@drawable` resource, not a `@color` or `@mipmap` resource.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !(parent instanceof Element)
                || !"resources".equals(((Element) parent).getTagName())) {
            return;
        }

        ResourceType aliasType = getAliasType(element);
        if (aliasType == null) {
            return;
        }

        ResourceUrl reference = getReferenceUrl(element);
        if (reference == null || reference.type == null) {
            return;
        }

        if (reference.type != aliasType) {
            String message = String.format(
                    "The %1$s resource declared here has an invalid reference to a %2$s resource",
                    aliasType.getName(), reference.type.getName());
            context.report(REFERENCE_TYPE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static ResourceType getAliasType(@NonNull Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return getTypeByName(type);
            }
            return null;
        }

        return getTypeByName(tag);
    }

    @Nullable
    private static ResourceType getTypeByName(@NonNull String name) {
        if ("string-array".equals(name) || "integer-array".equals(name)) {
            return ResourceType.ARRAY;
        }

        try {
            return ResourceType.valueOf(name.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static ResourceUrl getReferenceUrl(@NonNull Element element) {
        Node child = element.getFirstChild();
        if (child == null
                || child.getNodeType() != Node.TEXT_NODE
                || child.getNextSibling() != null) {
            return null;
        }

        String text = child.getNodeValue().trim();
        if (text.isEmpty()) {
            return null;
        }

        ResourceUrl url = ResourceUrl.parse(text);
        if (url == null || !url.isReference() || url.isCreate()) {
            return null;
        }

        return url;
    }
}