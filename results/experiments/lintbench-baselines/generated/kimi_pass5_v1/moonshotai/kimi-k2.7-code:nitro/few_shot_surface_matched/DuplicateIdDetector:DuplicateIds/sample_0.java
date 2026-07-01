package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner, Comparable<DuplicateIdDetector> {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    private static final String ATTR_ID = "id";
    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String ID_REF_PREFIX = "@id/";

    private Map<String, List<org.w3c.dom.Attr>> mIds;

    public DuplicateIdDetector() {
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<String, List<org.w3c.dom.Attr>>();
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<org.w3c.dom.Attr>> entry : mIds.entrySet()) {
            List<org.w3c.dom.Attr> occurrences = entry.getValue();
            if (occurrences.size() > 1) {
                String id = entry.getKey();
                String message = "Duplicate id @+id/" + id + ", already defined earlier in this layout";
                for (org.w3c.dom.Attr attribute : occurrences) {
                    xmlContext.report(ISSUE, attribute, xmlContext.getLocation(attribute), message);
                }
            }
        }
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
        if (!ATTR_ID.equals(attribute.getLocalName())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        String id;
        if (value.startsWith(NEW_ID_PREFIX)) {
            id = value.substring(NEW_ID_PREFIX.length());
        } else if (value.startsWith(ID_REF_PREFIX)) {
            id = value.substring(ID_REF_PREFIX.length());
        } else {
            return;
        }
        if (id.isEmpty()) {
            return;
        }
        List<org.w3c.dom.Attr> list = mIds.get(id);
        if (list == null) {
            list = new ArrayList<org.w3c.dom.Attr>();
            mIds.put(id, list);
        }
        list.add(attribute);
    }

    @Override
    public String toString() {
        return DuplicateIdDetector.class.getSimpleName();
    }

    @Override
    public int compareTo(DuplicateIdDetector other) {
        return toString().compareTo(other.toString());
    }
}