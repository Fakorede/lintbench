package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.FolderType;
import com.android.resources.ResourceType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders " +
            "specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add a widget to all variations " +
            "of the layout, which could result in a runtime crash for some resource configurations when " +
            "a `findViewById()` fails.\n\n" +
            "There **are** cases where this is intentional. For example, you may have a dedicated large " +
            "tablet layout which adds some extra widgets that are not present in the phone version of the " +
            "layout. As long as the code accessing the layout resource is careful to handle this properly, " +
            "it is valid. In that case, you can suppress this lint check for the given extra or missing " +
            "views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Map<String, Set<String>>> layoutIds = new HashMap<>();
    private final Map<String, Map<String, File>> layoutFiles = new HashMap<>();
    private final Set<String> referencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull FolderType folderType, boolean inManifest) {
        return folderType == FolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String baseName = fileName.substring(0, fileName.length() - 4);
        String folderName = context.file.getParentFile().getName();
        String config = folderName.startsWith("layout-") ? folderName.substring(7) : "base";

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        layoutIds.computeIfAbsent(baseName, k -> new HashMap<>()).put(config, ids);
        layoutFiles.computeIfAbsent(baseName, k -> new HashMap<>()).put(config, context.file);
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UElement node,
            @NonNull ResourceType type, @NonNull String name, boolean isFramework,
            @Nullable PsiClass targetClass, @Nullable PsiMethod targetMethod) {
        if (type == ResourceType.LAYOUT) {
            referencedLayouts.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : layoutIds.entrySet()) {
            String layoutName = entry.getKey();
            if (!referencedLayouts.contains(layoutName)) {
                continue;
            }

            Map<String, Set<String>> configs = entry.getValue();
            if (configs.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : configs.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> configEntry : configs.entrySet()) {
                String config = configEntry.getKey();
                Set<String> configIds = configEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(configIds);

                if (!missing.isEmpty()) {
                    File file = layoutFiles.get(layoutName).get(config);
                    Location location = Location.create(file);
                    String msg = String.format(
                            "The layout `%s` in configuration `%s` is missing the following widgets: `%s`. " +
                            "This can lead to a `NullPointerException` at runtime.",
                            layoutName, config, String.join(", ", missing));
                    context.report(ISSUE, location, msg);
                }
            }
        }

        layoutIds.clear();
        layoutFiles.clear();
        referencedLayouts.clear();
    }

    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            if (id.startsWith("@+id/")) {
                ids.add(id.substring(5));
            } else if (id.startsWith("@id/")) {
                ids.add(id.substring(4));
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
}