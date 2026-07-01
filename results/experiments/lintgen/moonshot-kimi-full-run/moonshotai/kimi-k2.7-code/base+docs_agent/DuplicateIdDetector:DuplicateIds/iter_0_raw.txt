package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a layout",
            "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<Element>> mIds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "id");
        if (attr == null) {
            return;
        }

        String value = attr.getValue();
        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null) {
            return;
        }

        String id = url.name;
        if (id.isEmpty()) {
            return;
        }

        mIds.computeIfAbsent(id, k -> new ArrayList<>()).add(element);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<Element>> entry : mIds.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() <= 1) {
                continue;
            }

            String message = "Duplicate id `@+id/" + entry.getKey() + "` in this layout";
            for (Element element : elements) {
                Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "id");
                if (attr == null) {
                    continue;
                }
                Location location = xmlContext.getLocation(attr);
                xmlContext.report(ISSUE, element, location, message);
            }
        }
        mIds = null;
    }
}