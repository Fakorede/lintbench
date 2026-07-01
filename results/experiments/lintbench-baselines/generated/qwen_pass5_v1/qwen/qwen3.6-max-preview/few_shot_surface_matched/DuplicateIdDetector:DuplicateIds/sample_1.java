package com.android.tools.lint.checks;

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
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise findViewById() can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Set<String> mIds;

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
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
    public void beforeCheckFile(Context context) {
        mIds = new HashSet<>();
    }

    @Override
    public void afterCheckFile(Context context) {
        mIds = null;
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
        if (mIds == null) {
            return;
        }

        String ns = attribute.getNamespaceURI();
        if (ns == null || !ns.equals("http://schemas.android.com/apk/res/android")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String idName = value;
        int slashIndex = value.indexOf('/');
        if (slashIndex != -1) {
            idName = value.substring(slashIndex + 1);
        }

        if (!mIds.add(idName)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Duplicate id `" + idName + "` within this layout");
        }
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