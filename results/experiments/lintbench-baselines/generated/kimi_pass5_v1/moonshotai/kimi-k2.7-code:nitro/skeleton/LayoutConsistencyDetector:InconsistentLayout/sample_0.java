package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_ID = "id";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "Checks that layout resources which are defined in multiple configuration folders "
                            + "contain the same set of widgets. If one configuration is missing a view "
                            + "that is present in another, a `findViewById()` call can fail at runtime. "
                            + "This may be intentional in some cases and can be suppressed with "
                            + "tools:ignore if needed.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutVariant>> mVariants = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        File file = context.getFile();
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        String baseName = file.getName().replaceFirst("\\.xml$", "");
        String key = ResourceFolderType.LAYOUT.getName() + "/" + baseName;

        List<LayoutVariant> variants = mVariants.get(key);
        if (variants == null) {
            variants = new ArrayList<>();
            mVariants.put(key, variants);
        }
        variants.add(new LayoutVariant(file, folderName, ids));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutVariant>> entry : mVariants.entrySet()) {
            List<LayoutVariant> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> union = new HashSet<>();
            Set<String> intersection = new HashSet<>(variants.get(0).ids);
            for (LayoutVariant variant : variants) {
                union.addAll(variant.ids);
                intersection.retainAll(variant.ids);
            }

            if (union.equals(intersection)) {
                continue;
            }

            for (LayoutVariant variant : variants) {
                Set<String> missing = new HashSet<>(union);
                missing.removeAll(variant.ids);

                Set<String> extra = new HashSet<>(variant.ids);
                extra.removeAll(intersection);

                if (missing.isEmpty() && extra.isEmpty()) {
                    continue;
                }

                StringBuilder message = new StringBuilder();
                message.append("Layout '")
                        .append(entry.getKey())
                        .append("' is inconsistent in '")
                        .append(variant.folderName)
                        .append("'");
                if (!missing.isEmpty()) {
                    message.append("; missing views: ").append(missing);
                }
                if (!extra.isEmpty()) {
                    message.append("; extra views: ").append(extra);
                }

                context.report(
                        ISSUE,
                        Location.create(variant.file),
                        message.toString());
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull ResourceReference reference,
            @NonNull PsiElement element) {
        // Not used.
    }

    private void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, ANDROID_ID);
        if (id != null && !id.isEmpty()) {
            String name = getResourceName(id);
            if (name != null && !name.isEmpty()) {
                ids.add(name);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    private static String getResourceName(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash + 1 >= value.length()) {
            return null;
        }
        String name = value.substring(slash + 1);
        // Strip a leading "+" from "@+id/..." if present.
        if (name.startsWith("+")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }

    private static class LayoutVariant {
        final File file;
        final String folderName;
        final Set<String> ids;

        LayoutVariant(File file, String folderName, Set<String> ids) {
            this.file = file;
            this.folderName = folderName;
            this.ids = ids;
        }
    }
}