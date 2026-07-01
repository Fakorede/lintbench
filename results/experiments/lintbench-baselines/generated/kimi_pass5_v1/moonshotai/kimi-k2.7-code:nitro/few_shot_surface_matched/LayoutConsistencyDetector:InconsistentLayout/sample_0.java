package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class LayoutConsistencyDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue INCONSISTENT_LAYOUT =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource "
                            + "folders, specifies the same set of widgets. This finds cases where you "
                            + "have accidentally forgotten to add a widget to all variations of the "
                            + "layout, which could result in a runtime crash for some resource "
                            + "configurations when a `findViewById()` fails.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<LayoutRecord>> mLayoutRecords;
    private Map<String, List<Location>> mLayoutReferences;
    private Map<String, List<Location>> mIdReferences;

    private static class LayoutRecord {
        final File file;
        final Set<String> ids;

        LayoutRecord(File file, Set<String> ids) {
            this.file = file;
            this.ids = ids;
        }
    }

    public LayoutConsistencyDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context) {
        if (mLayoutRecords == null) {
            mLayoutRecords = new HashMap<>();
        }

        Set<String> ids = new HashSet<>();
        collectIds(context.document.getDocumentElement(), ids);

        String layoutName = LintUtils.getBaseName(context.file);
        List<LayoutRecord> records = mLayoutRecords.get(layoutName);
        if (records == null) {
            records = new ArrayList<>();
            mLayoutRecords.put(layoutName, records);
        }
        records.add(new LayoutRecord(context.file, ids));
    }

    private static void collectIds(@Nullable Node node, @NonNull Set<String> ids) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            ids.add(id.substring(SdkConstants.NEW_ID_PREFIX.length()));
        }

        Node child = element.getFirstChild();
        while (child != null) {
            collectIds(child, ids);
            child = child.getNextSibling();
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework,
            boolean isGetOrSet) {
        if (isFramework) {
            return;
        }

        if (type == ResourceType.LAYOUT) {
            if (mLayoutReferences == null) {
                mLayoutReferences = new HashMap<>();
            }
            addLocation(mLayoutReferences, name, context.getLocation(node));
        } else if (type == ResourceType.ID) {
            if (mIdReferences == null) {
                mIdReferences = new HashMap<>();
            }
            addLocation(mIdReferences, name, context.getLocation(node));
        }
    }

    private static void addLocation(
            @NonNull Map<String, List<Location>> map,
            @NonNull String name,
            @NonNull Location location) {
        List<Location> locations = map.get(name);
        if (locations == null) {
            locations = new ArrayList<>();
            map.put(name, locations);
        }
        locations.add(location);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context, @NonNull Project project) {
        if (mLayoutRecords == null || mLayoutReferences == null || mIdReferences == null) {
            return;
        }

        for (Map.Entry<String, List<Location>> layoutEntry : mLayoutReferences.entrySet()) {
            String layoutName = layoutEntry.getKey();
            List<LayoutRecord> records = mLayoutRecords.get(layoutName);
            if (records == null || records.size() < 2) {
                continue;
            }

            for (String id : mIdReferences.keySet()) {
                boolean presentInSome = false;
                boolean missingInSome = false;
                for (LayoutRecord record : records) {
                    if (record.ids.contains(id)) {
                        presentInSome = true;
                    } else {
                        missingInSome = true;
                    }
                }

                if (!presentInSome || !missingInSome) {
                    continue;
                }

                String message =
                        String.format(
                                "The id '%1$s' is referenced from code but is not present in every "
                                        + "version of the layout '%2$s'; some device configurations "
                                        + "are missing it, which can cause `findViewById()` to fail "
                                        + "at runtime",
                                id, layoutName);

                for (LayoutRecord record : records) {
                    if (!record.ids.contains(id)) {
                        context.report(
                                INCONSISTENT_LAYOUT,
                                Location.create(record.file),
                                message);
                    }
                }
            }
        }
    }
}