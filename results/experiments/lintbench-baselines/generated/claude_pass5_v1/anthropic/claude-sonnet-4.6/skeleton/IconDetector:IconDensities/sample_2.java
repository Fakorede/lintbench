package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

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

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE));

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

    // Standard density folders we check (excluding ldpi by default)
    private static final String[] DENSITY_FOLDERS = {
        "drawable-mdpi",
        "drawable-hdpi",
        "drawable-xhdpi",
        "drawable-xxhdpi"
    };

    private static final String[] DENSITY_FOLDERS_WITH_LDPI = {
        "drawable-ldpi",
        "drawable-mdpi",
        "drawable-hdpi",
        "drawable-xhdpi",
        "drawable-xxhdpi"
    };

    private static final String INCLUDE_LDPI_ENV = "ANDROID_LINT_INCLUDE_LDPI";

    // Map from icon name to set of density folders it appears in
    private final Map<String, Set<String>> iconFolderMap = new HashMap<>();

    // Track which density folders we've seen
    private final Set<String> seenDensityFolders = new HashSet<>();

    // Map from icon name to the file (for reporting location)
    private final Map<String, File> iconLocationMap = new HashMap<>();

    // Map from icon name to XmlContext (for reporting)
    private final Map<String, XmlContext> iconContextMap = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconFolderMap.clear();
        seenDensityFolders.clear();
        iconLocationMap.clear();
        iconContextMap.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        boolean includeLdpi = Boolean.parseBoolean(System.getenv(INCLUDE_LDPI_ENV));
        String[] densityFolders = includeLdpi ? DENSITY_FOLDERS_WITH_LDPI : DENSITY_FOLDERS;

        // Only check densities that we've actually seen in the project
        List<String> relevantDensities = new ArrayList<>();
        for (String density : densityFolders) {
            if (seenDensityFolders.contains(density)) {
                relevantDensities.add(density);
            }
        }

        if (relevantDensities.size() < 2) {
            // Not enough density folders to compare
            return;
        }

        // For each icon, check if it's missing from any density folder
        for (Map.Entry<String, Set<String>> entry : iconFolderMap.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentFolders = entry.getValue();

            List<String> missingFolders = new ArrayList<>();
            for (String density : relevantDensities) {
                if (!presentFolders.contains(density)) {
                    missingFolders.add(density);
                }
            }

            if (!missingFolders.isEmpty() && !missingFolders.equals(relevantDensities)) {
                // Icon is present in some but not all density folders
                XmlContext xmlContext = iconContextMap.get(iconName);
                File iconFile = iconLocationMap.get(iconName);

                if (xmlContext != null && iconFile != null) {
                    StringBuilder message = new StringBuilder();
                    message.append("The icon `").append(iconName).append("` does not have a ");
                    if (missingFolders.size() == 1) {
                        message.append("drawable in folder `").append(missingFolders.get(0)).append("`");
                    } else {
                        message.append("drawable in the following folders: ");
                        for (int i = 0; i < missingFolders.size(); i++) {
                            if (i > 0) {
                                message.append(", ");
                            }
                            message.append("`").append(missingFolders.get(i)).append("`");
                        }
                    }

                    Incident incident = new Incident(ISSUE, message.toString(),
                            xmlContext.getLocation(iconFile));
                    context.report(incident);
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File file = context.file;
        if (file == null) {
            return;
        }

        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        if (!isDensityFolder(folderName)) {
            return;
        }

        seenDensityFolders.add(folderName);

        // Get the icon name (file name without extension)
        String fileName = file.getName();
        String iconName = getIconName(fileName);

        // Track this icon in this density folder
        iconFolderMap.computeIfAbsent(iconName, k -> new HashSet<>()).add(folderName);

        // Store location info (prefer the first occurrence for reporting)
        if (!iconLocationMap.containsKey(iconName)) {
            iconLocationMap.put(iconName, file);
            iconContextMap.put(iconName, context);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this check
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for this check
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this check
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this check
            }
        };
    }

    /**
     * Checks if a folder name is a density-specific drawable folder.
     */
    private boolean isDensityFolder(@NonNull String folderName) {
        if (!folderName.startsWith("drawable-") && !folderName.startsWith("mipmap-")) {
            return false;
        }

        // Check for density qualifiers
        String[] densities = {"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (String density : densities) {
            if (folderName.contains(density)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the base density folder name (e.g., "drawable-mdpi") from a full folder name,
     * stripping other qualifiers if present.
     */
    private String getBaseDensityFolder(@NonNull String folderName) {
        String[] densities = {"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        String prefix = folderName.startsWith("mipmap-") ? "drawable" : "drawable";

        if (folderName.startsWith("mipmap-")) {
            for (String density : densities) {
                if (folderName.contains(density)) {
                    return "drawable-" + density;
                }
            }
        } else {
            for (String density : densities) {
                if (folderName.contains(density)) {
                    return "drawable-" + density;
                }
            }
        }
        return folderName;
    }

    /**
     * Returns the icon name without extension.
     */
    private String getIconName(@NonNull String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    // Override visitFile to track drawable files directly (including PNGs, JPGs, etc.)
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        if (!isDensityFolder(folderName)) {
            return;
        }

        String fileName = file.getName();
        // Only process image files
        if (!isImageFile(fileName)) {
            return;
        }

        String baseFolderName = getBaseDensityFolder(folderName);
        seenDensityFolders.add(baseFolderName);

        String iconName = getIconName(fileName);

        // Track this icon in this density folder
        iconFolderMap.computeIfAbsent(iconName, k -> new HashSet<>()).add(baseFolderName);

        // Store location info
        if (!iconLocationMap.containsKey(iconName)) {
            iconLocationMap.put(iconName, file);
        }
    }

    /**
     * Checks if a file is an image file based on its extension.
     */
    private boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".xml");
    }
}