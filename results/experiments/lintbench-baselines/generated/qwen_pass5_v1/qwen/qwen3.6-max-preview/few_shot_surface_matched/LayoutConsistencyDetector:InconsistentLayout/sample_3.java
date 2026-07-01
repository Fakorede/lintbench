package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders "
            + "specifies the same set of widgets. This finds cases where you have accidentally forgotten "
            + "to add a widget to all variations of the layout, which could result in a runtime crash "
            + "for some resource configurations when a `findViewById()` fails.\n\n"
            + "There **are** cases where this is intentional. For example, you may have a dedicated "
            + "large tablet layout which adds some extra widgets that are not present in the phone version "
            + "of the layout. As long as the code accessing the layout resource is careful to handle this "
            + "properly, it is valid. In that case, you can suppress this lint check for the given extra "
            + "or missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    private static class LayoutVariation {
        final String qualifier;
        final File file;
        final Set<String> ids;

        LayoutVariation(String qualifier, File file, Set<String> ids) {
            this.qualifier = qualifier;
            this.file = file;
            this.ids = ids;
        }
    }

    private final Map<String, List<LayoutVariation>> mLayouts = new HashMap<>();
    private final Set<String> mReferencedIds = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) return;

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) return;

        String layoutName = fileName.substring(0, fileName.length() - 4);
        File parent = context.file.getParentFile();
        String qualifier = parent != null ? parent.getName() : "";

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        List<LayoutVariation> variations = mLayouts.computeIfAbsent(layoutName, k -> new ArrayList<>());
        variations.add(new LayoutVariation(qualifier, context.file, ids));
    }

    private void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            int slash = id.indexOf('/');
            if (slash != -1 && slash + 1 < id.length()) {
                ids.add(id.substring(slash + 1));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UExpression node,
            @NonNull ResourceType type, @NonNull String name, boolean isFramework,
            @Nullable PsiElement resolved) {
        if (type == ResourceType.ID) {
            mReferencedIds.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutVariation>> entry : mLayouts.entrySet()) {
            List<LayoutVariation> variations = entry.getValue();
            if (variations.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (LayoutVariation v : variations) {
                allIds.addAll(v.ids);
            }

            for (LayoutVariation v : variations) {
                for (String id : allIds) {
                    if (!v.ids.contains(id) && mReferencedIds.contains(id)) {
                        String message = String.format(
                                "The id `%1$s` is defined in other configurations of `%2$s` but not here",
                                id, entry.getKey());
                        context.report(ISSUE, Location.create(v.file), message);
                    }
                }
            }
        }
    }
}