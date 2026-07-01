package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ICON_DENSITIES =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). This "
                            + "lint check identifies icons which do not have complete coverage "
                            + "across the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)))
                    .setMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final boolean INCLUDE_LDPI =
            Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private static final List<String> DENSITY_ORDER =
            Arrays.asList("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");

    private static final Set<String> KNOWN_DENSITIES = new HashSet<>(DENSITY_ORDER);
    private static final Set<String> REQUIRED_DENSITIES =
            new LinkedHashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi"));

    private final Map<String, Set<String>> mIconDensities = new HashMap<>();
    private final Map<String, Location> mIconLocations = new HashMap<>();
    private final Set<String> mReferencedIcons = new HashSet<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        recordIcon(file);
        String path = file.getPath();
        return path.endsWith(".java")
                || path.endsWith(".kt")
                || path.contains("/res/")
                || path.endsWith(".xml");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mIconDensities.clear();
        mIconLocations.clear();
        mReferencedIcons.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
                "ImageView",
                "ImageButton",
                "item",
                "bitmap");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            org.w3c.dom.Node attribute = attributes.item(i);
            if (attribute == null) {
                continue;
            }
            String value = attribute.getNodeValue();
            if (value != null
                    && (value.startsWith("@drawable/") || value.startsWith("@mipmap/"))) {
                int index = value.indexOf('/');
                if (index != -1 && index < value.length() - 1) {
                    String name = stripExtension(value.substring(index + 1));
                    if (!name.isEmpty()) {
                        mReferencedIcons.add(name);
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Set<String> required = new LinkedHashSet<>(REQUIRED_DENSITIES);
        if (INCLUDE_LDPI) {
            required.add("ldpi");
        }

        for (Map.Entry<String, Set<String>> entry : mIconDensities.entrySet()) {
            String name = entry.getKey();
            Set<String> found = entry.getValue();
            Set<String> missing = new LinkedHashSet<>(required);
            missing.removeAll(found);
            if (missing.isEmpty()) {
                continue;
            }

            Location location = mIconLocations.get(name);
            if (location == null) {
                continue;
            }

            String message = buildMessage(name, found, missing);
            context.report(ICON_DENSITIES, location, message, name);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UClass.class);
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {}

            @Override
            public void visitMethod(UMethod node) {}

            @Override
            public void visitCallExpression(UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {}
        };
    }

    private void recordIcon(File file) {
        if (file.isDirectory()) {
            return;
        }
        String fileName = file.getName();
        if (fileName.endsWith(".xml")) {
            return;
        }
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        String density = extractDensity(parent.getName());
        if (density == null) {
            return;
        }
        String name = stripExtension(fileName);
        if (name.isEmpty()) {
            return;
        }
        mIconDensities.computeIfAbsent(name, k -> new HashSet<>()).add(density);
        mIconLocations.computeIfAbsent(name, k -> Location.create(file));
    }

    private String extractDensity(String folderName) {
        if (!folderName.startsWith("drawable-") && !folderName.startsWith("mipmap-")) {
            return null;
        }
        int dash = folderName.indexOf('-');
        if (dash == -1 || dash == folderName.length() - 1) {
            return null;
        }
        String qualifiers = folderName.substring(dash + 1);
        for (String token : qualifiers.split("-")) {
            if (KNOWN_DENSITIES.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private String stripExtension(String name) {
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private String buildMessage(String name, Set<String> found, Set<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("Missing density variations for icon `").append(name).append("`");
        sb.append(" (found: ");
        appendOrdered(sb, found);
        sb.append("; missing: ");
        appendOrdered(sb, missing);
        sb.append(")");
        return sb.toString();
    }

    private void appendOrdered(StringBuilder sb, Set<String> densities) {
        boolean first = true;
        for (String density : DENSITY_ORDER) {
            if (densities.contains(density)) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(density);
                first = false;
            }
        }
    }
}