package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference type",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(DuplicateResourceDetector.class, Scope.ALL_RESOURCES));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_ITEM = "item";

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        // No per-file state is required for the reference-type check.
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.charAt(0) != '@') {
            return;
        }

        String ownerTag = attribute.getOwnerElement().getTagName();
        if (TAG_RESOURCES.equals(ownerTag)) {
            return;
        }

        String aliasType;
        if (TAG_ITEM.equals(ownerTag)) {
            aliasType = attribute.getOwnerElement().getAttribute(ATTR_TYPE);
        } else {
            aliasType = ownerTag;
        }
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        com.android.resources.ResourceType resourceType =
                com.android.resources.ResourceType.fromXmlTag(aliasType);
        if (resourceType == null) {
            return;
        }

        com.android.resources.ResourceUrl url =
                com.android.resources.ResourceUrl.parse(value);
        if (url == null || url.type == null || url.type == resourceType) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Expected reference of type "
                        + resourceType.getName()
                        + " but was @"
                        + url.type.getName()
                        + "/"
                        + url.name);
    }
}