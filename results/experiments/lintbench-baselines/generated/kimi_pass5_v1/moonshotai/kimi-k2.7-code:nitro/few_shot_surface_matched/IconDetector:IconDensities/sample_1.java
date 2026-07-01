package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the major "
                            + "screen density classes (low, medium, high, extra high). This check "
                            + "identifies icons which do not have complete coverage across the "
                            + "densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String LDPI_ENV = "ANDROID_LINT_INCLUDE_LDPI";

    private static final Set<String> DENSITY_QUALIFIERS =
            new HashSet<>(
                    Arrays.asList(
                            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "tvdpi"));

    private static final Set<String> DEFAULT_REQUIRED_DENSITIES =
            new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi"));

    private final Map<String, IconInfo> mIcons = new HashMap<>();

    private static class IconInfo {
        final File file;
        final Set<String> densities = new HashSet<>();

        IconInfo(File file) {
            this.file = file;
        }
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mIcons.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Set<String> required = getRequiredDensities();
        for (Map.Entry<String, IconInfo> entry : mIcons.entrySet()) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(entry.getValue().densities);
            if (!missing.isEmpty()) {
                String message =
                        "The icon `"
                                + entry.getKey()
                                + "` is missing density variations (found: "
                                + formatDensities(entry.getValue().densities)
                                + "; missing: "
                                + formatDensities(missing)
                                + ")";
                context.report(ISSUE, Location.create(entry.getValue().file), message);
            }
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, Severity severity) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        if (!file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".png")
                && !name.endsWith(".jpg")
                && !name.endsWith(".jpeg")
                && !name.endsWith(".webp")
                && !name.endsWith(".gif")) {
            return false;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }

        String density = getDensity(parent.getName());
        if (density == null) {
            return false;
        }

        String baseName = getBaseName(file.getName());
        IconInfo info = mIcons.get(baseName);
        if (info == null) {
            info = new IconInfo(file);
            mIcons.put(baseName, info);
        }
        info.densities.add(density);

        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                UMethod.class,
                UClass.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public org.jetbrains.uast.visitor.UElementHandler createUastHandler(JavaContext context) {
        return new org.jetbrains.uast.visitor.UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
            }

            @Override
            public void visitClass(UClass node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            }
        };
    }

    private static Set<String> getRequiredDensities() {
        Set<String> required = new HashSet<>(DEFAULT_REQUIRED_DENSITIES);
        if ("true".equalsIgnoreCase(System.getenv(LDPI_ENV))) {
            required.add("ldpi");
        }
        return required;
    }

    private static String getDensity(String folderName) {
        if ("drawable".equals(folderName) || "mipmap".equals(folderName)) {
            return "mdpi";
        }

        String prefix = "drawable-";
        if (!folderName.startsWith(prefix)) {
            prefix = "mipmap-";
            if (!folderName.startsWith(prefix)) {
                return null;
            }
        }

        String qualifier = folderName.substring(prefix.length());
        for (String part : qualifier.split("-")) {
            if (DENSITY_QUALIFIERS.contains(part)) {
                return part;
            }
        }
        return null;
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String formatDensities(Set<String> densities) {
        List<String> sorted = Arrays.asList(densities.toArray(new String[0]));
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }
}