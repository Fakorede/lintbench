package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";
    private static final String KEY_NAME = "name";
    private static final String KEY_DENSITY = "density";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). "
                            + "This lint check identifies icons which do not have complete coverage "
                            + "across the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<Icon>> mIcons;
    private Set<File> mSeenXmlFiles;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIcons = new HashMap<>();
        mSeenXmlFiles = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mIcons == null || mIcons.isEmpty()) {
            return;
        }

        boolean includeLdpi = "true".equals(System.getenv(ANDROID_LINT_INCLUDE_LDPI));
        List<Density> required = new ArrayList<>(
                Arrays.asList(
                        Density.MDPI,
                        Density.HDPI,
                        Density.XHDPI,
                        Density.XXHDPI,
                        Density.XXXHDPI));
        if (includeLdpi) {
            required.add(Density.LDPI);
        }

        for (Map.Entry<String, List<Icon>> entry : mIcons.entrySet()) {
            List<Icon> icons = entry.getValue();
            Set<Density> present = new HashSet<>();
            boolean hasDefault = false;
            Icon firstSpecific = null;

            for (Icon icon : icons) {
                if (icon.density == null
                        || icon.density == Density.NODPI
                        || icon.density == Density.ANYDPI) {
                    hasDefault = true;
                    break;
                }
                present.add(icon.density);
                if (firstSpecific == null) {
                    firstSpecific = icon;
                }
            }

            if (hasDefault || firstSpecific == null) {
                continue;
            }

            String baseName = entry.getKey();
            for (Density density : required) {
                if (!present.contains(density)) {
                    String densityName = density.getResourceValue();
                    String message =
                            "The icon `"
                                    + baseName
                                    + "` is missing the density variation: "
                                    + densityName;
                    LintMap map = new LintMap();
                    map.put(KEY_NAME, baseName);
                    map.put(KEY_DENSITY, densityName);
                    Incident incident = new Incident(ISSUE, firstSpecific.location, message, map);
                    context.report(incident);
                }
            }
        }

        mIcons.clear();
        mSeenXmlFiles.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        String density = map.getString(KEY_DENSITY, null);
        if ("ldpi".equals(density)
                && !"true".equals(System.getenv(ANDROID_LINT_INCLUDE_LDPI))) {
            return false;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        File file = context.file;
        if (mSeenXmlFiles.contains(file)) {
            return;
        }
        mSeenXmlFiles.add(file);

        Density density = getDensity(context);
        String baseName = getBaseName(file.getName());
        recordIcon(file, baseName, density, folderType);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        Density density = getDensity(context);
        String baseName = getBaseName(context.file.getName());
        recordIcon(context.file, baseName, density, folderType);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for icon density validation.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for icon density validation.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for icon density validation.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for icon density validation.
            }
        };
    }

    private void recordIcon(
            @NonNull File file,
            @NonNull String baseName,
            @Nullable Density density,
            @NonNull ResourceFolderType folderType) {
        String key = folderType == ResourceFolderType.MIPMAP ? "mipmap:" + baseName : baseName;
        List<Icon> list = mIcons.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(new Icon(file, baseName, density, folderType, Location.create(file)));
    }

    @Nullable
    private static Density getDensity(@NonNull ResourceContext context) {
        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null) {
            return null;
        }
        DensityQualifier qualifier = config.getDensityQualifier();
        return qualifier == null ? null : qualifier.getValue();
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static class Icon {
        final File file;
        final String name;
        final Density density;
        final ResourceFolderType type;
        final Location location;

        Icon(
                @NonNull File file,
                @NonNull String name,
                @Nullable Density density,
                @NonNull ResourceFolderType type,
                @NonNull Location location) {
            this.file = file;
            this.name = name;
            this.density = density;
            this.type = type;
            this.location = location;
        }
    }
}