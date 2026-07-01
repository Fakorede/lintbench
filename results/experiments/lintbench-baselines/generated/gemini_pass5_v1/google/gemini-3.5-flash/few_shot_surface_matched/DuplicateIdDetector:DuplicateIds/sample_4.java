package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner, Comparable<Detector> {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateIdDetector.class, Scope.LAYOUT_SCOPE));

    private final Set<String> mIds = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mIds.clear();
    }

    @Override
    public void afterCheckFile(XmlContext context) {
    }

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckRootProject(Context context) {
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@+id/") || value.startsWith("@id/")) {
            String id = value.substring(value.indexOf('/') + 1);
            if (mIds.contains(id)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Duplicate id `" + value + "`, already defined earlier in this layout");
            } else {
                mIds.add(id);
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return this.toString().compareTo(other.toString());
    }
}