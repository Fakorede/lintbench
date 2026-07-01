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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String ID_PREFIX_PLUS = "@+id/";
    private static final String ID_PREFIX = "@id/";

    private Map<String, List<Attr>> mIds;

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        java.io.File parent = file.getParentFile();
        return parent != null && parent.getName().startsWith("layout");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("merge");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mIds == null) {
            return;
        }
        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> attributes = entry.getValue();
            if (attributes.size() > 1) {
                reportDuplicate(context, entry.getKey(), attributes);
            }
        }
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No cross-file state is required for duplicate ids within a single layout.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // No cross-file state is required for duplicate ids within a single layout.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // The <merge> root does not represent an actual View in the final layout.
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_NAMESPACE.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner != null && "merge".equals(owner.getTagName())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        String id = extractId(value);
        if (id == null) {
            return;
        }

        List<Attr> attributes = mIds.get(id);
        if (attributes == null) {
            attributes = new ArrayList<>();
            mIds.put(id, attributes);
        }
        attributes.add(attribute);
    }

    private void reportDuplicate(Context context, String id, List<Attr> attributes) {
        XmlContext xmlContext = (XmlContext) context;
        String message = "Duplicate id `" + id + "`, already defined in this layout";
        for (Attr attribute : attributes) {
            xmlContext.report(
                    ISSUE,
                    attribute,
                    xmlContext.getLocation(attribute),
                    message);
        }
    }

    private static String extractId(String value) {
        if (value.startsWith(ID_PREFIX_PLUS)) {
            return value.substring(ID_PREFIX_PLUS.length());
        }
        if (value.startsWith(ID_PREFIX)) {
            return value.substring(ID_PREFIX.length());
        }
        return null;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int compareTo(Detector other) {
        return getName().compareTo(other.getName());
    }
}