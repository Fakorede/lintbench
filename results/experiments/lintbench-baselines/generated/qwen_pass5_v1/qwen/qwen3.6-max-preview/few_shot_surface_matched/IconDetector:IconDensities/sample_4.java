package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see "
                    + "https://developer.android.com/about/dashboards",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";
    private static final List<String> BASE_DENSITIES = Arrays.asList("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");

    private final Set<String> referencedIcons = new HashSet<>();
    private List<String> targetDensities;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        targetDensities = new java.util.ArrayList<>(BASE_DENSITIES);
        if (Boolean.parseBoolean(System.getenv(ENV_INCLUDE_LDPI))) {
            targetDensities.add(0, "ldpi");
        }
        referencedIcons.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        File projectDir = context.getProject().getDir();
        File resDir = new File(projectDir, "res");
        if (!resDir.exists()) {
            return;
        }

        for (String icon : referencedIcons) {
            Set<String> missingDensities = new HashSet<>();
            for (String density : targetDensities) {
                File densityDir = new File(resDir, "drawable-" + density);
                if (!densityDir.exists()) {
                    missingDensities.add(density);
                    continue;
                }
                boolean found = false;
                File[] files = densityDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        int dotIndex = name.lastIndexOf('.');
                        String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;
                        if (baseName.equals(icon)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    missingDensities.add(density);
                }
            }

            if (!missingDensities.isEmpty() && missingDensities.size() < targetDensities.size()) {
                context.report(ISSUE, context.getLocation(projectDir),
                        "Icon `" + icon + "` is missing density variants for: " + String.join(", ", missingDensities));
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String value = attr.getNodeValue();
            if (value != null && value.startsWith("@drawable/")) {
                String icon = value.substring("@drawable/".length());
                if (!icon.startsWith("?") && !icon.startsWith("@android:")) {
                    referencedIcons.add(icon);
                }
            }
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Hook for method-level analysis if required
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Hook for call expression analysis if required
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                // Hook for class-level analysis if required
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String identifier = node.getIdentifier();
                UElement parent = node.getUastParent();
                if (parent != null && "drawable".equals(parent.getQualifiedName())) {
                    referencedIcons.add(identifier);
                }
            }
        };
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                USimpleNameReferenceExpression.class,
                UCallExpression.class,
                UMethod.class,
                UClass.class
        );
    }
}