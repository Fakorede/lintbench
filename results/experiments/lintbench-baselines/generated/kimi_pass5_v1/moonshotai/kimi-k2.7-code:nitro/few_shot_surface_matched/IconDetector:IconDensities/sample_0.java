package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.FilterableDetector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, FilterableDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "IconDensities",
                    "Icon densities validation",
                    "Icons will look best if a custom version is provided for each of the "
                            + "major screen density classes (low, medium, high, extra high). "
                            + "This check identifies icons which do not have complete coverage "
                            + "across the densities.\n\n"
                            + "Low density is not really used much anymore, so this check ignores "
                            + "the ldpi density. To force lint to include it, set the environment "
                            + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                            + "current density usage, see "
                            + "https://developer.android.com/about/dashboards",
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final Set<Density> REQUIRED_DENSITIES =
            EnumSet.of(
                    Density.MEDIUM,
                    Density.HIGH,
                    Density.XHIGH,
                    Density.XXHIGH,
                    Density.XXXHIGH);

    private final Map<String, Map<Density, File>> mIconDensityFiles = new HashMap<>();
    private final Set<String> mReferencedIcons = new HashSet<>();
    private boolean mIncludeLdpi;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconDensityFiles.clear();
        mReferencedIcons.clear();
        mIncludeLdpi = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull ResourceFile file) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0, n = attrs.getLength(); i < n; i++) {
            Attr attr = (Attr) attrs.item(i);
            String value = attr.getValue();
            if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
                int slash = value.indexOf('/');
                if (slash != -1 && slash + 1 < value.length()) {
                    String name = value.substring(slash + 1);
                    if (!name.isEmpty()) {
                        mReferencedIcons.add(name);
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(4);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        types.add(UMethod.class);
        types.add(UClass.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                // Not used for density validation.
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for density validation.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for density validation.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiField) {
                    PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                    if (containingClass != null) {
                        String className = containingClass.getName();
                        if ("drawable".equals(className) || "mipmap".equals(className)) {
                            mReferencedIcons.add(node.getIdentifier());
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        File[] resourceDirs = project.getResourceDirectories();
        if (resourceDirs == null) {
            return;
        }

        for (File resDir : resourceDirs) {
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                if (!typeDir.isDirectory()) {
                    continue;
                }
                String dirName = typeDir.getName();
                boolean isDrawableFolder = dirName.startsWith("drawable");
                boolean isMipmapFolder = dirName.startsWith("mipmap");
                if (!isDrawableFolder && !isMipmapFolder) {
                    continue;
                }
                Density density = parseDensity(dirName);
                if (density == null
                        || density == Density.NODPI
                        || density == Density.ANYDPI) {
                    continue;
                }
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String fileName = file.getName();
                    if (fileName.endsWith(".xml") || fileName.endsWith(".9.png")) {
                        continue;
                    }
                    int dot = fileName.lastIndexOf('.');
                    String iconName = dot > 0 ? fileName.substring(0, dot) : fileName;
                    mIconDensityFiles
                            .computeIfAbsent(iconName, k -> new HashMap<>())
                            .put(density, file);
                }
            }
        }

        Set<Density> required = EnumSet.copyOf(REQUIRED_DENSITIES);
        if (mIncludeLdpi) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, Map<Density, File>> entry : mIconDensityFiles.entrySet()) {
            String iconName = entry.getKey();
            Set<Density> present = entry.getValue().keySet();
            if (present.isEmpty()) {
                continue;
            }

            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);
            if (missing.isEmpty()) {
                continue;
            }

            File locationFile = entry.getValue().values().iterator().next();
            Location location = Location.create(locationFile);

            StringBuilder message = new StringBuilder();
            message.append("Missing density variation for icon `").append(iconName).append("`");
            message.append(" (missing: ");
            boolean first = true;
            for (Density density : missing) {
                if (!first) {
                    message.append(", ");
                }
                first = false;
                message.append(getDensityFolderName(density));
            }
            message.append(")");

            context.report(ISSUE, location, message.toString());
        }
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Nullable
    private static Density parseDensity(@NonNull String folderName) {
        int dash = folderName.indexOf('-');
        if (dash == -1 || dash + 1 >= folderName.length()) {
            return null;
        }
        String qualifiers = folderName.substring(dash + 1);
        for (String qualifier : qualifiers.split("-")) {
            Density density = getDensityByQualifier(qualifier);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    @Nullable
    private static Density getDensityByQualifier(@NonNull String qualifier) {
        switch (qualifier) {
            case "ldpi":
                return Density.LOW;
            case "mdpi":
                return Density.MEDIUM;
            case "hdpi":
                return Density.HIGH;
            case "xhdpi":
                return Density.XHIGH;
            case "xxhdpi":
                return Density.XXHIGH;
            case "xxxhdpi":
                return Density.XXXHIGH;
            case "nodpi":
                return Density.NODPI;
            case "anydpi":
                return Density.ANYDPI;
            case "tvdpi":
                return Density.TV;
            default:
                return null;
        }
    }

    @NonNull
    private static String getDensityFolderName(@NonNull Density density) {
        switch (density) {
            case LOW:
                return "ldpi";
            case MEDIUM:
                return "mdpi";
            case HIGH:
                return "hdpi";
            case XHIGH:
                return "xhdpi";
            case XXHIGH:
                return "xxhdpi";
            case XXXHIGH:
                return "xxxhdpi";
            case TV:
                return "tvdpi";
            case NODPI:
                return "nodpi";
            case ANYDPI:
                return "anydpi";
            default:
                return density.name().toLowerCase();
        }
    }
}