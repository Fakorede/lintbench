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

    // Expected sizes for launcher icons by density folder name
    private static final Map<String, int[]> EXPECTED_SIZES;

    static {
        EXPECTED_SIZES = new HashMap<>();
        EXPECTED_SIZES.put("mipmap-ldpi",    new int[]{36, 36});
        EXPECTED_SIZES.put("mipmap-mdpi",    new int[]{48, 48});
        EXPECTED_SIZES.put("mipmap-hdpi",    new int[]{72, 72});
        EXPECTED_SIZES.put("mipmap-xhdpi",   new int[]{96, 96});
        EXPECTED_SIZES.put("mipmap-xxhdpi",  new int[]{144, 144});
        EXPECTED_SIZES.put("mipmap-xxxhdpi", new int[]{192, 192});
        EXPECTED_SIZES.put("drawable-ldpi",  new int[]{36, 36});
        EXPECTED_SIZES.put("drawable-mdpi",  new int[]{48, 48});
        EXPECTED_SIZES.put("drawable-hdpi",  new int[]{72, 72});
        EXPECTED_SIZES.put("drawable-xhdpi", new int[]{96, 96});
        EXPECTED_SIZES.put("drawable-xxhdpi",  new int[]{144, 144});
        EXPECTED_SIZES.put("drawable-xxxhdpi", new int[]{192, 192});
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to finalize
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Accept all incidents by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We handle file-based resources, not XML elements specifically.
        // Return empty so we rely on file scanning.
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for icon size checking
    }

    /**
     * Called for each resource file. We check image files for correct dimensions.
     */
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();

        // Only check PNG, JPEG, GIF, WebP image files
        String lowerName = name.toLowerCase();
        if (!lowerName.endsWith(".png")
                && !lowerName.endsWith(".jpg")
                && !lowerName.endsWith(".jpeg")
                && !lowerName.endsWith(".gif")
                && !lowerName.endsWith(".webp")) {
            return;
        }

        // Get the parent folder name to determine density
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }
        String folderName = parentFolder.getName();

        // Check if this folder has expected sizes
        int[] expectedSize = EXPECTED_SIZES.get(folderName);
        if (expectedSize == null) {
            return;
        }

        // Read the image dimensions
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            return;
        }

        if (image == null) {
            return;
        }

        int actualWidth = image.getWidth();
        int actualHeight = image.getHeight();
        int expectedWidth = expectedSize[0];
        int expectedHeight = expectedSize[1];

        if (actualWidth != expectedWidth || actualHeight != expectedHeight) {
            String message = String.format(
                    "Incorrect icon size for `%1$s`: expected %2$dx%3$d, but was %4$dx%5$d",
                    folderName + "/" + name,
                    expectedWidth, expectedHeight,
                    actualWidth, actualHeight);

            context.report(
                    new Incident(
                            ISSUE,
                            message,
                            context.getLocation(file)));
        }
    }

    // SourceCodeScanner methods — not used in this detector but required by skeleton

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }
}