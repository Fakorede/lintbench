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
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You "
                            + "should follow these conventions to make sure your icons fit in with the "
                            + "overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Expected sizes for launcher icons by density qualifier
    // Format: density folder suffix -> expected dimension (width x height)
    private static final Map<String, Dimension> EXPECTED_SIZES;

    static {
        EXPECTED_SIZES = new HashMap<>();
        EXPECTED_SIZES.put("ldpi", new Dimension(36, 36));
        EXPECTED_SIZES.put("mdpi", new Dimension(48, 48));
        EXPECTED_SIZES.put("hdpi", new Dimension(72, 72));
        EXPECTED_SIZES.put("xhdpi", new Dimension(96, 96));
        EXPECTED_SIZES.put("xxhdpi", new Dimension(144, 144));
        EXPECTED_SIZES.put("xxxhdpi", new Dimension(192, 192));
    }

    // Launcher icon file name patterns
    private static final List<String> LAUNCHER_ICON_NAMES = Arrays.asList(
            "ic_launcher",
            "ic_launcher_round",
            "ic_launcher_foreground",
            "ic_launcher_background"
    );

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize before checking root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to clean up after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We handle image files directly, not XML elements in this simplified version
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handle XML elements if needed - for vector drawables etc.
        // In this implementation we focus on bitmap icons
    }

    /**
     * Check icon files when the project is being analyzed.
     * This is called for resource files - we check PNG/JPEG icon files.
     */
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        String name = file.getName();
        if (!isImageFile(name)) {
            return;
        }

        // Get the base name without extension
        String baseName = getBaseName(name);
        if (!isLauncherIcon(baseName)) {
            return;
        }

        // Determine density from parent folder name
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String folderName = parentFolder.getName();
        String density = getDensity(folderName);
        if (density == null) {
            return;
        }

        Dimension expectedSize = EXPECTED_SIZES.get(density);
        if (expectedSize == null) {
            return;
        }

        // Read the actual image size
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }

            int actualWidth = image.getWidth();
            int actualHeight = image.getHeight();

            if (actualWidth != expectedSize.width || actualHeight != expectedSize.height) {
                String message = String.format(
                        "Launcher icons for %s density should be %dx%d px, but this icon is %dx%d px",
                        density,
                        expectedSize.width,
                        expectedSize.height,
                        actualWidth,
                        actualHeight);

                context.report(
                        new Incident(
                                ISSUE,
                                message,
                                context.getLocation(file)));
            }
        } catch (IOException e) {
            // Could not read image, skip
        }
    }

    /**
     * Returns true if the given file name is an image file.
     */
    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp");
    }

    /**
     * Returns the base name of a file (without extension).
     */
    private static String getBaseName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * Returns true if the given base name looks like a launcher icon.
     */
    private static boolean isLauncherIcon(String baseName) {
        for (String iconName : LAUNCHER_ICON_NAMES) {
            if (baseName.equals(iconName) || baseName.startsWith(iconName + "_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the density qualifier from a resource folder name.
     * E.g., "drawable-hdpi" -> "hdpi", "mipmap-xxhdpi" -> "xxhdpi"
     */
    private static String getDensity(String folderName) {
        // Handle drawable-<density> and mipmap-<density>
        int dashIndex = folderName.indexOf('-');
        if (dashIndex == -1) {
            return null;
        }

        // Could have multiple qualifiers, e.g. drawable-en-hdpi
        // We look for known density qualifiers
        String[] parts = folderName.substring(dashIndex + 1).split("-");
        for (String part : parts) {
            if (EXPECTED_SIZES.containsKey(part)) {
                return part;
            }
        }

        return null;
    }
}