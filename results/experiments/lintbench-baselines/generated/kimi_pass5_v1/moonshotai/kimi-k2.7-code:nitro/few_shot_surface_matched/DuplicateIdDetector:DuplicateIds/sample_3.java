package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner,
        Comparable<DuplicateIdDetector> {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs within a single layout",
            "Within a layout, ids should be unique since otherwise `findViewById()` can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    private static final String ID_PREFIX = "@+id/";
    private static final String ID_REF_PREFIX = "@id/";

    private java.util.Map<String, java.util.List<org.w3c.dom.Element>> mIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return ResourceXmlDetector.ALL;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("id");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new java.util.HashMap<>();
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mIds == null) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (java.util.Map.Entry<String, java.util.List<org.w3c.dom.Element>> entry
                : mIds.entrySet()) {
            java.util.List<org.w3c.dom.Element> elements = entry.getValue();
            if (elements.size() > 1) {
                String id = entry.getKey();
                String message = String.format(
                        "Duplicate id %1$s defined %2$d times in this layout",
                        id, elements.size());
                for (org.w3c.dom.Element element : elements) {
                    xmlContext.report(
                            ISSUE,
                            element,
                            xmlContext.getLocation(element),
                            message);
                }
            }
        }
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.startsWith(ID_PREFIX)) {
            value = ID_REF_PREFIX + value.substring(ID_PREFIX.length());
        }
        java.util.List<org.w3c.dom.Element> elements = mIds.get(value);
        if (elements == null) {
            elements = new java.util.ArrayList<>();
            mIds.put(value, elements);
        }
        elements.add(attribute.getOwnerElement());
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(DuplicateIdDetector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}