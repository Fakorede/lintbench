package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.imageio.ImageIO;

/**
 * Checks for icons that are missing density variants.
 */
public class IconDetector extends ResourceXmlDetector implements Detector.BinaryResourceScanner {

    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    /** Main issue: missing density folders */
    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). " +
            "This lint check identifies icons which do not have complete coverage " +
            "across the densities.\n" +
            "\n" +
            "Low density is not really used much anymore, so this check ignores " +
            "the ldpi density. To force lint to include it, set the environment " +
            "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
            "current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.ALL_RESOURCES_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    // Density folder names
    private static final String DRAWABLE_LDPI   = "drawable-ldpi";
    private static final String DRAWABLE_MDPI   = "drawable-mdpi";
    private static final String DRAWABLE_HDPI   = "drawable-hdpi";
    private static final String DRAWABLE_XHDPI  = "drawable-xhdpi";
    private static final String DRAWABLE_XXHDPI = "drawable-xxhdpi";
    private static final String DRAWABLE_XXXHDPI= "drawable-xxxhdpi";

    private static final List<String> DENSITY_FOLDERS_NO_LDPI = Arrays.asList(
            DRAWABLE_MDPI,
            DRAWABLE_HDPI,
            DRAWABLE_XHDPI,
            DRAWABLE_XXHDPI
    );

    private static final List<String> DENSITY_FOLDERS_WITH_LDPI = Arrays.asList(
            DRAWABLE_LDPI,
            DRAWABLE_MDPI,
            DRAWABLE_HDPI,
            DRAWABLE_XHDPI,
            DRAWABLE_XXHDPI
    );

    /**
     * Map from icon name to the set of density folders in which it appears.
     * Key: icon filename (e.g. "ic_launcher.png")
     * Value: set of density folder names containing this icon
     */
    private final Map<String, Set<String>> iconInDensities = new HashMap<>();

    /**
     * The resource directory (res/) we are scanning.
     */
    private File resDir = null;

    /** Constructor */
    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconInDensities.clear();
        resDir = null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We don't need XML element scanning; return null
        return null;
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderName = folder.getName();

        // Only consider drawable-*dpi folders
        if (!isDensityFolder(folderName)) {
            return;
        }

        // Track the res directory
        if (resDir == null) {
            resDir = folder.getParentFile();
        }

        String iconName = file.getName();

        // Only check image files
        if (!isImageFile(iconName)) {
            return;
        }

        Set<String> densities = iconInDensities.get(iconName);
        if (densities == null) {
            densities = new HashSet<>();
            iconInDensities.put(iconName, densities);
        }
        densities.add(folderName);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (iconInDensities.isEmpty()) {
            return;
        }

        boolean includeLdpi = includeLdpi();
        List<String> requiredDensities = includeLdpi
                ? DENSITY_FOLDERS_WITH_LDPI
                : DENSITY_FOLDERS_NO_LDPI;

        // For each icon, check whether it appears in all required density folders
        // (only if it appears in at least one density folder)
        for (Map.Entry<String, Set<String>> entry : iconInDensities.entrySet()) {
            String iconName = entry.getKey();
            Set<String> presentDensities = entry.getValue();

            // Find which required densities are missing
            List<String> missing = new ArrayList<>();
            for (String required : requiredDensities) {
                if (!presentDensities.contains(required)) {
                    missing.add(required);
                }
            }

            if (!missing.isEmpty() && !missing.equals(requiredDensities)) {
                // Only report if at least one density IS present (i.e. it's intentionally
                // a density-specific icon but missing some variants)
                // Sort for deterministic output
                Collections.sort(missing);

                String message = String.format(
                        "The icon `%1$s` appears to be missing the following densities: `%2$s`",
                        iconName,
                        formatList(missing));

                // Try to find a representative file to attach the location to
                Location location = guessLocation(context, iconName, presentDensities);
                context.report(ICON_DENSITIES, location, message);
            }
        }
    }

    // ---- Helper methods ----

    private static boolean isDensityFolder(@NonNull String folderName) {
        return folderName.equals(DRAWABLE_LDPI)
                || folderName.equals(DRAWABLE_MDPI)
                || folderName.equals(DRAWABLE_HDPI)
                || folderName.equals(DRAWABLE_XHDPI)
                || folderName.equals(DRAWABLE_XXHDPI)
                || folderName.equals(DRAWABLE_XXXHDPI);
    }

    private static boolean isImageFile(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private static boolean includeLdpi() {
        String value = System.getenv(ANDROID_LINT_INCLUDE_LDPI);
        return "true".equalsIgnoreCase(value);
    }

    @NonNull
    private static String formatList(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    @NonNull
    private Location guessLocation(@NonNull Context context,
            @NonNull String iconName,
            @NonNull Set<String> presentDensities) {
        if (resDir != null) {
            // Try to find the file in one of the present density folders
            for (String density : presentDensities) {
                File candidate = new File(resDir, density + File.separator + iconName);
                if (candidate.exists()) {
                    return Location.create(candidate);
                }
            }
            // Fall back to res dir
            return Location.create(resDir);
        }
        return Location.create(context.file);
    }
}