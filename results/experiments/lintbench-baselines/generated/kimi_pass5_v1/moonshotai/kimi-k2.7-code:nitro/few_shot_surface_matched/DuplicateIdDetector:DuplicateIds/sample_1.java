package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate Ids Within a Single Layout",
                    "Within a layout, ids should be unique since otherwise `findViewById()` can"
                            + " return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private java.util.Map<String, java.util.List<org.w3c.dom.Element>> mIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(com.android.SdkConstants.ATTR_ID);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mIds = new java.util.HashMap<>();
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        for (java.util.Map.Entry<String, java.util.List<org.w3c.dom.Element>> entry :
                mIds.entrySet()) {
            java.util.List<org.w3c.dom.Element> elements = entry.getValue();
            if (elements.size() > 1) {
                String id = "@+id/" + entry.getKey();
                for (org.w3c.dom.Element element : elements) {
                    org.w3c.dom.Attr attr =
                            element.getAttributeNodeNS(
                                    com.android.SdkConstants.ANDROID_URI,
                                    com.android.SdkConstants.ATTR_ID);
                    Location location =
                            attr != null ? context.getLocation(attr) : context.getLocation(element);
                    context.report(
                            ISSUE,
                            element,
                            location,
                            "Duplicate id " + id + " within this layout");
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
        if (!com.android.SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        String id = stripIdPrefix(value);
        if (id == null || id.isEmpty()) {
            return;
        }

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        java.util.List<org.w3c.dom.Element> list = mIds.get(id);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mIds.put(id, list);
        }
        list.add(owner);
    }

    private static String stripIdPrefix(String value) {
        if (value.startsWith("@+id/")) {
            return value.substring("@+id/".length());
        }
        if (value.startsWith("@id/")) {
            return value.substring("@id/".length());
        }
        if (value.startsWith("@android:id/")) {
            return value.substring("@android:id/".length());
        }
        return value;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}