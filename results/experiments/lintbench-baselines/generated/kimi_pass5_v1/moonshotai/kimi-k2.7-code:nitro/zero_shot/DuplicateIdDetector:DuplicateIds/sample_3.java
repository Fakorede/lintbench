package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs in a layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<Element>> mIds;

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.LAYOUT);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String idValue = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        String id = getIdName(idValue);
        if (id == null) {
            return;
        }

        List<Element> elements = mIds.get(id);
        if (elements == null) {
            elements = new ArrayList<>();
            mIds.put(id, elements);
        }
        elements.add(element);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        if (mIds == null) {
            return;
        }

        for (Map.Entry<String, List<Element>> entry : mIds.entrySet()) {
            List<Element> elements = entry.getValue();
            if (elements.size() <= 1) {
                continue;
            }

            Location first = xmlContext.getLocation(elements.get(0));
            List<Location> duplicates = new ArrayList<>();
            for (int i = 1; i < elements.size(); i++) {
                duplicates.add(xmlContext.getLocation(elements.get(i)));
            }

            Location location = Location.create(
                    first.getFile(),
                    first.getStart(),
                    first.getEnd(),
                    duplicates);

            xmlContext.report(
                    ISSUE,
                    location,
                    "Duplicate id @+id/" + entry.getKey() + " in this layout");
        }
    }

    private static String getIdName(@NonNull String idValue) {
        if (idValue.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            return idValue.substring(SdkConstants.NEW_ID_PREFIX.length());
        }
        if (idValue.startsWith(SdkConstants.ANDROID_ID_PREFIX)) {
            return idValue.substring(SdkConstants.ANDROID_ID_PREFIX.length());
        }
        return null;
    }
}