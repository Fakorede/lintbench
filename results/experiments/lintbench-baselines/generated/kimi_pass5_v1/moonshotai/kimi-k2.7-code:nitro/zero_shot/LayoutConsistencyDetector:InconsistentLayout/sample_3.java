package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent layout",
            "This check ensures that a layout resource which is defined in multiple resource "
                    + "folders specifies the same set of widgets. This finds cases where you "
                    + "have accidentally forgotten to add a widget to all variations of the "
                    + "layout, which could result in a runtime crash for some resource "
                    + "configurations when a `findViewById()` fails.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<LayoutInfo>> mLayouts = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mLayouts.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        File file = context.file;
        String layoutName = file.getName();
        if (layoutName.endsWith(SdkConstants.DOT_XML)) {
            layoutName = layoutName.substring(0, layoutName.length() - SdkConstants.DOT_XML.length());
        }

        File parent = file.getParentFile();
        String folder = parent != null ? parent.getName() : "";

        List<LayoutInfo> variants = mLayouts.get(layoutName);
        if (variants == null) {
            variants = new ArrayList<>();
            mLayouts.put(layoutName, variants);
        }
        variants.add(new LayoutInfo(file, folder, ids));
    }

    private static void collectIds(@NonNull Node node, @NonNull Set<String> ids) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (id != null && !id.isEmpty()) {
                ids.add(LintUtils.stripIdPrefix(id));
            }
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectIds(child, ids);
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : mLayouts.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> union = new HashSet<>();
            for (LayoutInfo info : variants) {
                union.addAll(info.ids);
            }

            for (LayoutInfo info : variants) {
                Set<String> missing = new HashSet<>(union);
                missing.removeAll(info.ids);
                if (missing.isEmpty()) {
                    continue;
                }

                String message = "The layout `" + layoutName + "` in `" + info.folder
                        + "` is missing the following widget ids which are present in other "
                        + "configurations: " + joinIds(missing);

                context.report(ISSUE, Location.create(info.file), message);
            }
        }
    }

    @NonNull
    private static String joinIds(@NonNull Set<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : new TreeSet<>(ids)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('`').append(id).append('`');
        }
        return sb.toString();
    }

    private static class LayoutInfo {
        final File file;
        final String folder;
        final Set<String> ids;

        LayoutInfo(@NonNull File file, @NonNull String folder, @NonNull Set<String> ids) {
            this.file = file;
            this.folder = folder;
            this.ids = ids;
        }
    }
}