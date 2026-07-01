package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final java.util.Map<String, java.util.Set<String>> mLayoutConfigs =
            new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Map<String, java.util.Set<String>>> mLayoutIds =
            new java.util.HashMap<>();
    private final java.util.Map<String,
            java.util.Map<String,
                    java.util.Map<String, Location>>> mLocations = new java.util.HashMap<>();
    private final java.util.Set<String> mReferencedIds = new java.util.HashSet<>();

    public static final Issue INCONSISTENT_LAYOUT =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets. This finds "
                            + "cases where you have accidentally forgotten to add a widget to all "
                            + "variations of the layout, which could result in a runtime crash "
                            + "for some resource configurations when a findViewById() fails. "
                            + "There are cases where this is intentional; you can suppress this "
                            + "lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            java.util.EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    @Override
    public boolean appliesTo(com.android.resources.ResourceType type) {
        return type == com.android.resources.ResourceType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String layout = getLayoutName(context);
        String config = getConfigName(context);

        mLayoutConfigs.computeIfAbsent(layout, k -> new java.util.HashSet<>()).add(config);

        collectIds(context, root, layout, config);

        org.w3c.dom.NodeList children = root.getElementsByTagName("*");
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Element child = (org.w3c.dom.Element) children.item(i);
            collectIds(context, child, layout, config);
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, java.util.Set<String>> layoutEntry
                : mLayoutConfigs.entrySet()) {
            String layout = layoutEntry.getKey();
            java.util.Set<String> allConfigs = layoutEntry.getValue();
            if (allConfigs.size() < 2) {
                continue;
            }

            java.util.Map<String, java.util.Set<String>> ids = mLayoutIds.get(layout);
            if (ids == null) {
                continue;
            }

            for (java.util.Map.Entry<String, java.util.Set<String>> idEntry : ids.entrySet()) {
                String id = idEntry.getKey();
                java.util.Set<String> present = idEntry.getValue();

                if (present == null || present.size() == allConfigs.size()) {
                    continue;
                }

                if (!mReferencedIds.contains(id)) {
                    continue;
                }

                java.util.Set<String> missing = new java.util.HashSet<>(allConfigs);
                missing.removeAll(present);
                if (missing.isEmpty()) {
                    continue;
                }

                Location location = findLocation(layout, id, present);
                if (location == null) {
                    continue;
                }

                java.util.List<String> missingList = new java.util.ArrayList<>(missing);
                java.util.Collections.sort(missingList);

                StringBuilder message = new StringBuilder();
                message.append("The id 'R.id.").append(id).append("' in layout '")
                        .append(layout).append("' is present in some configurations but not others")
                        .append(" (missing from");
                boolean first = true;
                for (String cfg : missingList) {
                    if (!first) {
                        message.append(",");
                    }
                    message.append(' ').append(cfg);
                    first = false;
                }
                message.append("). This may cause a runtime crash when findViewById(R.id.")
                        .append(id).append(") is called.");

                context.report(INCONSISTENT_LAYOUT, location, message.toString());
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            JavaContext context,
            UElement node,
            com.android.resources.ResourceType type,
            String name,
            boolean isFramework) {
        if (!isFramework && type == com.android.resources.ResourceType.ID) {
            mReferencedIds.add(name);
        }
    }

    private void collectIds(
            XmlContext context,
            org.w3c.dom.Element element,
            String layout,
            String config) {
        org.w3c.dom.Attr attr = element.getAttributeNodeNS(ANDROID_NS, "id");
        if (attr == null) {
            return;
        }

        String value = attr.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String id = getIdName(value);
        if (id == null) {
            return;
        }

        java.util.Map<String, java.util.Set<String>> ids =
                mLayoutIds.computeIfAbsent(layout, k -> new java.util.HashMap<>());
        ids.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(config);

        java.util.Map<String, java.util.Map<String, Location>> byConfig =
                mLocations.computeIfAbsent(layout, k -> new java.util.HashMap<>());
        java.util.Map<String, Location> byId =
                byConfig.computeIfAbsent(config, k -> new java.util.HashMap<>());
        byId.put(id, context.getLocation(attr));
    }

    private Location findLocation(String layout, String id, java.util.Set<String> presentConfigs) {
        java.util.Map<String, java.util.Map<String, Location>> byConfig = mLocations.get(layout);
        if (byConfig == null) {
            return null;
        }
        for (String cfg : presentConfigs) {
            java.util.Map<String, Location> locs = byConfig.get(cfg);
            if (locs != null && locs.containsKey(id)) {
                return locs.get(id);
            }
        }
        return null;
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String getConfigName(XmlContext context) {
        java.io.File parent = context.file.getParentFile();
        return parent != null ? parent.getName() : "";
    }

    private String getIdName(String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        String prefix = value.substring(0, slash);
        if ("@+id".equals(prefix) || "@id".equals(prefix)) {
            return value.substring(slash + 1);
        }
        return null;
    }
}