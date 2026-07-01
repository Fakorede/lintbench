package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.ResourceValue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ICON_DENSITIES =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the major"
                            + " screen density classes (low, medium, high, extra high). This lint"
                            + " check identifies icons which do not have complete coverage across"
                            + " the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores"
                            + " the ldpi density. To force lint to include it, set the environment"
                            + " variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on"
                            + " current density usage, see"
                            + " https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE))
            .setMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> IMAGE_EXTENSIONS =
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "9.png");

    private final Map<String, Location> mIconLocations = new HashMap<>();
    private final Set<String> mUsedIcons = new HashSet<>();
    private final Set<String> mReported = new HashSet<>();
    private boolean mIncludeLdpi;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconLocations.clear();
        mUsedIcons.clear();
        mReported.clear();
        String includeLdpi = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        mIncludeLdpi =
                includeLdpi != null
                        && (includeLdpi.equalsIgnoreCase("true") || includeLdpi.equals("1"));
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            ResourceValue value = ResourceEvaluator.getResource(context, attribute);
            if (value != null
                    && (value.getType() == ResourceType.DRAWABLE
                            || value.getType() == ResourceType.MIPMAP)
                    && !value.isFramework()) {
                String name = value.getName();
                if (name != null) {
                    mUsedIcons.add(name);
                    if (!mIconLocations.containsKey(name)) {
                        mIconLocations.put(name, context.getLocation(attribute));
                    }
                }
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new IconUastHandler(context);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        types.add(UQualifiedReferenceExpression.class);
        types.add(UClass.class);
        types.add(UMethod.class);
        return types;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Map<String, Set<String>> densities = collectDensities(context);

        Set<String> names = new HashSet<>(densities.keySet());
        names.addAll(mUsedIcons);

        Set<String> required = getRequiredDensities();

        for (String name : names) {
            Set<String> found = densities.get(name);
            if (found == null || found.isEmpty()) {
                continue;
            }
            if (found.contains("anydpi") || found.contains("nodpi")) {
                continue;
            }

            Set<String> missing = new HashSet<>(required);
            missing.removeAll(found);
            if (missing.isEmpty()) {
                continue;
            }

            Location location = mIconLocations.get(name);
            if (location == null) {
                location = findFirstFileLocation(context, name);
            }
            if (location == null) {
                continue;
            }

            List<String> missingList = new ArrayList<>(missing);
            Collections.sort(missingList);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missingList.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(missingList.get(i));
            }

            context.report(
                    ICON_DENSITIES,
                    location,
                    "The icon '"
                            + name
                            + "' is missing density variations for: "
                            + sb);
        }
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        if (incident.getIssue() != ICON_DENSITIES) {
            return true;
        }
        String message = incident.getMessage();
        int start = message.indexOf('\'');
        int end = message.indexOf('\'', start + 1);
        if (start != -1 && end != -1) {
            String name = message.substring(start + 1, end);
            return mReported.add(name);
        }
        return true;
    }

    private Map<String, Set<String>> collectDensities(@NonNull Context context) {
        Map<String, Set<String>> densities = new HashMap<>();
        Project project = context.getProject();
        List<File> resourceFiles = project.getResourceFiles();
        if (resourceFiles == null) {
            return densities;
        }
        for (File file : resourceFiles) {
            File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }
            String parentName = parent.getName();
            if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
                continue;
            }
            String fileName = file.getName();
            String extension = getExtension(fileName);
            if (!IMAGE_EXTENSIONS.contains(extension)) {
                continue;
            }
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String density = extractDensity(parentName);
            densities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
        }
        return densities;
    }

    private Location findFirstFileLocation(@NonNull Context context, @NonNull String name) {
        Project project = context.getProject();
        List<File> resourceFiles = project.getResourceFiles();
        if (resourceFiles == null) {
            return null;
        }
        for (File file : resourceFiles) {
            File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }
            String parentName = parent.getName();
            if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
                continue;
            }
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
            if (baseName.equals(name)) {
                return Location.create(file);
            }
        }
        return null;
    }

    private Set<String> getRequiredDensities() {
        Set<String> required = new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi"));
        if (mIncludeLdpi) {
            required.add("ldpi");
        }
        return required;
    }

    private static String extractDensity(@NonNull String folderName) {
        if (folderName.equals("drawable") || folderName.equals("mipmap")) {
            return "default";
        }
        String[] parts = folderName.split("-");
        for (String part : parts) {
            switch (part) {
                case "ldpi":
                    return "ldpi";
                case "mdpi":
                    return "mdpi";
                case "tvdpi":
                    return "tvdpi";
                case "hdpi":
                    return "hdpi";
                case "xhdpi":
                    return "xhdpi";
                case "xxhdpi":
                    return "xxhdpi";
                case "xxxhdpi":
                    return "xxxhdpi";
                case "nodpi":
                    return "nodpi";
                case "anydpi":
                    return "anydpi";
                default:
                    // continue searching
            }
        }
        return "default";
    }

    private static String getExtension(@NonNull String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(index + 1).toLowerCase();
        if (fileName.regionMatches(true, index - 2, ".9.png", 0, 6)) {
            return "9.png";
        }
        return ext;
    }

    private class IconUastHandler extends UElementHandler {
        private final JavaContext mContext;

        IconUastHandler(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            for (UExpression argument : node.getValueArguments()) {
                recordDrawable(argument);
            }
        }

        @Override
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            recordDrawable(node);
        }

        @Override
        public void visitQualifiedReferenceExpression(
                @NonNull UQualifiedReferenceExpression node) {
            recordDrawable(node);
        }

        @Override
        public void visitClass(@NonNull UClass node) {}

        @Override
        public void visitMethod(@NonNull UMethod node) {}

        private void recordDrawable(@NonNull UElement node) {
            ResourceValue value = ResourceEvaluator.getResource(mContext, node);
            if (value != null
                    && (value.getType() == ResourceType.DRAWABLE
                            || value.getType() == ResourceType.MIPMAP)
                    && !value.isFramework()) {
                String name = value.getName();
                if (name != null) {
                    mUsedIcons.add(name);
                    if (!mIconLocations.containsKey(name)) {
                        mIconLocations.put(name, mContext.getLocation(node));
                    }
                }
            }
        }
    }
}