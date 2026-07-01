package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private Map<String, List<Element>> mIds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<Element>> entry : mIds.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() > 1) {
                String id = entry.getKey();
                for (int i = 1; i < elements.size(); i++) {
                    Element element = elements.get(i);
                    Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
                    if (attr != null) {
                        xmlContext.report(
                                ISSUE,
                                attr,
                                xmlContext.getLocation(attr),
                                String.format(
                                        "Duplicate id %1$s, already defined earlier in this layout",
                                        id));
                    }
                }
            }
        }
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Duplicate ids are reported per layout file.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Duplicate ids are reported per layout file.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Detection is driven by the android:id attribute.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = normalizeId(value);
        if (id.isEmpty()) {
            return;
        }

        List<Element> list = mIds.get(id);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(id, list);
        }
        list.add(attribute.getOwnerElement());
    }

    private static String normalizeId(String value) {
        if (value.startsWith("@+id/")) {
            return "@id/" + value.substring("@+id/".length());
        }
        return value;
    }
}