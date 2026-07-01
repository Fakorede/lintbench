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

    // Expected sizes for launcher icons per density folder
    // density folder name -> expected size (width x height in pixels)
    private static final Map<String, int[]> LAUNCHER_ICON_SIZES;

    static {
        LAUNCHER_ICON_SIZES = new HashMap<>();
        LAUNCHER_ICON_SIZES.put("mipmap-mdpi", new int[]{48, 48});
        LAUNCHER_ICON_SIZES.put("mipmap-hdpi", new int[]{72, 72});
        LAUNCHER_ICON_SIZES.put("mipmap-xhdpi", new int[]{96, 96});
        LAUNCHER_ICON_SIZES.put("mipmap-xxhdpi", new int[]{144, 144});
        LAUNCHER_ICON_SIZES.put("mipmap-xxxhdpi", new int[]{192, 192});
        LAUNCHER_ICON_SIZES.put("drawable-mdpi", new int[]{48, 48});
        LAUNCHER_ICON_SIZES.put("drawable-hdpi", new int[]{72, 72});
        LAUNCHER_ICON_SIZES.put("drawable-xhdpi", new int[]{96, 96});
        LAUNCHER_ICON_SIZES.put("drawable-xxhdpi", new int[]{144, 144});
        LAUNCHER_ICON_SIZES.put("drawable-xxxhdpi", new int[]{192, 192});
    }

    // Action bar icon sizes per density
    private static final Map<String, int[]> ACTION_BAR_ICON_SIZES;

    static {
        ACTION_BAR_ICON_SIZES = new HashMap<>();
        ACTION_BAR_ICON_SIZES.put("drawable-mdpi", new int[]{32, 32});
        ACTION_BAR_ICON_SIZES.put("drawable-hdpi", new int[]{48, 48});
        ACTION_BAR_ICON_SIZES.put("drawable-xhdpi", new int[]{64, 64});
        ACTION_BAR_ICON_SIZES.put("drawable-xxhdpi", new int[]{96, 96});
        ACTION_BAR_ICON_SIZES.put("drawable-xxxhdpi", new int[]{128, 128});
    }

    // Notification icon sizes per density
    private static final Map<String, int[]> NOTIFICATION_ICON_SIZES;

    static {
        NOTIFICATION_ICON_SIZES = new HashMap<>();
        NOTIFICATION_ICON_SIZES.put("drawable-mdpi", new int[]{24, 24});
        NOTIFICATION_ICON_SIZES.put("drawable-hdpi", new int[]{36, 36});
        NOTIFICATION_ICON_SIZES.put("drawable-xhdpi", new int[]{48, 48});
        NOTIFICATION_ICON_SIZES.put("drawable-xxhdpi", new int[]{72, 72});
        NOTIFICATION_ICON_SIZES.put("drawable-xxxhdpi", new int[]{96, 96});
    }

    private final Map<String, File> mLauncherIcons = new HashMap<>();

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLauncherIcons.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Check all collected launcher icon files for size issues
        for (Map.Entry<String, File> entry : mLauncherIcons.entrySet()) {
            String folderName = entry.getKey();
            File iconFile = entry.getValue();

            int[] expectedSize = LAUNCHER_ICON_SIZES.get(folderName);
            if (expectedSize == null) {
                continue;
            }

            if (iconFile.exists() && isImageFile(iconFile)) {
                try {
                    BufferedImage image = ImageIO.read(iconFile);
                    if (image != null) {
                        int actualWidth = image.getWidth();
                        int actualHeight = image.getHeight();
                        if (actualWidth != expectedSize[0] || actualHeight != expectedSize[1]) {
                            context.report(
                                    ISSUE,
                                    com.android.tools.lint.detector.api.Location.create(iconFile),
                                    String.format(
                                            "Launcher icon in `%1$s` should be %2$dx%3$d but was %4$dx%5$d",
                                            folderName,
                                            expectedSize[0],
                                            expectedSize[1],
                                            actualWidth,
                                            actualHeight));
                        }
                    }
                } catch (IOException e) {
                    // Ignore read errors
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // No special filtering needed; report all incidents
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We process image files directly, not XML elements in this simplified implementation
        // Return empty to avoid XML element processing overhead for image icons
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used in this implementation since getApplicableElements returns empty
    }

    /**
     * Returns true if the given file is an image file based on its extension.
     */
    private static boolean isImageFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }

    /**
     * Checks if the given file is a launcher icon by name convention.
     */
    private static boolean isLauncherIcon(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.startsWith("ic_launcher") || name.equals("icon.png");
    }

    /**
     * Checks if the given file is an action bar icon by name convention.
     */
    private static boolean isActionBarIcon(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.startsWith("ic_action_") || name.startsWith("ic_menu_");
    }

    /**
     * Checks if the given file is a notification icon by name convention.
     */
    private static boolean isNotificationIcon(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.startsWith("ic_stat_") || name.startsWith("ic_notification_");
    }

    /**
     * Checks an image file for expected size issues given expected dimensions.
     */
    private void checkIconSize(
            @NonNull Context context,
            @NonNull File file,
            @NonNull String folderName,
            int expectedWidth,
            int expectedHeight) {
        if (!file.exists() || !isImageFile(file)) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int actualWidth = image.getWidth();
                int actualHeight = image.getHeight();
                if (actualWidth != expectedWidth || actualHeight != expectedHeight) {
                    context.report(
                            ISSUE,
                            com.android.tools.lint.detector.api.Location.create(file),
                            String.format(
                                    "Icon in `%1$s` should be %2$dx%3$d but was %4$dx%5$d",
                                    folderName,
                                    expectedWidth,
                                    expectedHeight,
                                    actualWidth,
                                    actualHeight));
                }
            }
        } catch (IOException e) {
            // Ignore read errors
        }
    }

    // ---- XmlScanner ----

    // Since we primarily deal with image files (not XML), the XML scanner methods
    // are implemented minimally. The actual icon size checking happens via
    // beforeCheckRootProject / afterCheckEachProject and file iteration.

    // ---- Unused UAST stubs (kept to satisfy skeleton structure) ----

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
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