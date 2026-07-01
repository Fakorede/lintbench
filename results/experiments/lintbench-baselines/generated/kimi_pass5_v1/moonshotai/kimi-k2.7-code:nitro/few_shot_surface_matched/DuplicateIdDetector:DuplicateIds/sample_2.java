package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<Location>> mIdLocations = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIdLocations.clear();
    }

    @Override
    public void afterCheckFile(Context context) {
        for (Map.Entry<String, List<Location>> entry : mIdLocations.entrySet()) {
            List<Location> locations = entry.getValue();
            if (locations.size() > 1) {
                String message =
                        "Duplicate id @id/" + entry.getKey() + " already defined in this layout";
                for (int i = 1; i < locations.size(); i++) {
                    context.report(ISSUE, locations.get(i), message);
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
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return;
        }

        String idName = value.substring(slash + 1);

        Location location = context.getLocation(attribute);
        List<Location> locations = mIdLocations.get(idName);
        if (locations == null) {
            locations = new ArrayList<>();
            mIdLocations.put(idName, locations);
        }
        locations.add(location);
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    @Override
    public int compareTo(Detector other) {
        return toString().compareTo(other.toString());
    }
}