package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "Checks that layout resources that are defined in multiple resource folders "
                    + "specify the same set of widgets, to avoid runtime `findViewById` failures.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<LayoutInstance>> mLayoutInstances;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mLayoutInstances = new HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mLayoutInstances == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);

        File folder = context.file.getParentFile();
        String variant = folder != null ? folder.getName() : "";

        Set<String> ids = new LinkedHashSet<>();
        collectIds(element, ids);

        LayoutInstance instance = new LayoutInstance(
                context.getLocation(element),
                variant,
                ids
        );

        mLayoutInstances.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(instance);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (mLayoutInstances == null) {
            return;
        }

        for (Map.Entry<String, List<LayoutInstance>> entry : mLayoutInstances.entrySet()) {
            List<LayoutInstance> instances = entry.getValue();
            if (instances.size() < 2) {
                continue;
            }

            LayoutInstance reference = findMostCommonInstance(instances);
            if (reference == null) {
                continue;
            }

            for (LayoutInstance instance : instances) {
                if (instance.ids.equals(reference.ids)) {
                    continue;
                }

                Set<String> missing = new LinkedHashSet<>(reference.ids);
                missing.removeAll(instance.ids);

                Set<String> extra = new LinkedHashSet<>(instance.ids);
                extra.removeAll(reference.ids);

                StringBuilder message = new StringBuilder();
                message.append("The layout \"").append(entry.getKey())
                        .append("\" has an inconsistent set of view ids across configurations.");
                if (!missing.isEmpty()) {
                    message.append(" The ").append(instance.variant)
                            .append(" version is missing: ").append(missing).append(".");
                }
                if (!extra.isEmpty()) {
                    message.append(" The ").append(instance.variant)
                            .append(" version has extra: ").append(extra).append(".");
                }

                context.report(ISSUE, instance.location, message.toString());
            }
        }

        mLayoutInstances = null;
    }

    private static LayoutInstance findMostCommonInstance(List<LayoutInstance> instances) {
        Map<Set<String>, Integer> counts = new HashMap<>();
        LayoutInstance mostCommon = null;
        int maxCount = 0;
        for (LayoutInstance instance : instances) {
            int count = counts.getOrDefault(instance.ids, 0) + 1;
            counts.put(instance.ids, count);
            if (count > maxCount) {
                maxCount = count;
                mostCommon = instance;
            }
        }
        return mostCommon;
    }

    private static void collectIds(Element element, Set<String> ids) {
        if (element.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            ids.add(stripIdPrefix(id));
        }

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        }
        if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    private static class LayoutInstance {
        final Location location;
        final String variant;
        final Set<String> ids;

        LayoutInstance(Location location, String variant, Set<String> ids) {
            this.location = location;
            this.variant = variant;
            this.ids = ids;
        }
    }
}