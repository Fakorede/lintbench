package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<Element>> mIds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        super.beforeCheckFile(context);
        mIds = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            List<Element> list = mIds.get(id);
            if (list == null) {
                list = new ArrayList<>();
                mIds.put(id, list);
            }
            list.add(element);
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        for (Map.Entry<String, List<Element>> entry : mIds.entrySet()) {
            List<Element> list = entry.getValue();
            if (list.size() > 1) {
                Location location = context.getLocation(list.get(0));
                Location current = location;
                for (int i = 1; i < list.size(); i++) {
                    Location next = context.getLocation(list.get(i));
                    current.setSecondary(next);
                    current = next;
                }
                context.report(ISSUE, location,
                        String.format("Duplicate id \"%1$s\", already defined in this layout",
                                entry.getKey()));
            }
        }
        mIds = null;
    }
}