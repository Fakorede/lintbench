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

    private static final String ATTR_NAME = "name";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";

    // Track launcher icon names discovered from the manifest
    private final java.util.Set<String> launcherIconNames = new java.util.HashSet<>();

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        launcherIconNames.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Accept all incidents by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We look for icon references in the manifest or resource files
        // Check if this is a manifest element with an icon attribute
        String icon = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "icon");
        if (icon != null && !icon.isEmpty()) {
            // Strip the @mipmap/ or @drawable/ prefix
            String iconName = stripResourcePrefix(icon);
            if (iconName != null) {
                launcherIconNames.add(iconName);
            }
        }

        // Also check the current file if it's an image file
        File file = context.file;
        checkIconFile(context, file);
    }

    /**
     * Checks whether the given image file has the correct size for its density bucket.
     */
    private void checkIconFile(@NonNull XmlContext context, @NonNull File file) {
        // Only check PNG/JPEG/GIF image files
        String name = file.getName();
        if (!isImageFile(name)) {
            return;
        }

        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String folderName = parentDir.getName();
        int[] expectedSize = EXPECTED_SIZES.get(folderName);
        if (expectedSize == null) {
            return;
        }

        // Check if this looks like a launcher icon (ic_launcher pattern or known icon)
        String baseName = getBaseName(name);
        boolean isLauncherIcon = baseName.contains("ic_launcher")
                || launcherIconNames.contains(baseName);
        if (!isLauncherIcon) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int expectedWidth = expectedSize[0];
            int expectedHeight = expectedSize[1];

            if (width != expectedWidth || height != expectedHeight) {
                String message = String.format(
                        "Incorrect icon size for `%1$s`: expected %2$dx%3$d, but was %4$dx%5$d",
                        folderName + "/" + name,
                        expectedWidth, expectedHeight,
                        width, height);
                context.report(ISSUE, context.getLocation(context.document.getDocumentElement()),
                        message);
            }
        } catch (IOException e) {
            // Can't read image, skip
        }
    }

    private static String stripResourcePrefix(String resourceRef) {
        if (resourceRef == null) {
            return null;
        }
        int slashIndex = resourceRef.indexOf('/');
        if (slashIndex >= 0) {
            return resourceRef.substring(slashIndex + 1);
        }
        return resourceRef;
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    private static String getBaseName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    // XmlScanner interface - these are not used in this simplified implementation
    // but must be present as stubs for compilation

    @Override
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

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }
}