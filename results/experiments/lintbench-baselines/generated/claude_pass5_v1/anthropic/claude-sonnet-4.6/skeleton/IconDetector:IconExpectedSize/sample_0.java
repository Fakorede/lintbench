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
import java.util.ArrayList;
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

public class IconDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
        EXPECTED_SIZES.put("mipmap-mdpi",    new int[]{48, 48});
        EXPECTED_SIZES.put("mipmap-hdpi",    new int[]{72, 72});
        EXPECTED_SIZES.put("mipmap-xhdpi",   new int[]{96, 96});
        EXPECTED_SIZES.put("mipmap-xxhdpi",  new int[]{144, 144});
        EXPECTED_SIZES.put("mipmap-xxxhdpi", new int[]{192, 192});
        EXPECTED_SIZES.put("drawable-mdpi",  new int[]{48, 48});
        EXPECTED_SIZES.put("drawable-hdpi",  new int[]{72, 72});
        EXPECTED_SIZES.put("drawable-xhdpi", new int[]{96, 96});
        EXPECTED_SIZES.put("drawable-xxhdpi",  new int[]{144, 144});
        EXPECTED_SIZES.put("drawable-xxxhdpi", new int[]{192, 192});
    }

    // Track icon files found in resource folders for checking
    private final List<File> iconFiles = new ArrayList<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconFiles.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (File iconFile : iconFiles) {
            checkIconSize(context, iconFile);
        }
        iconFiles.clear();
    }

    private void checkIconSize(@NonNull Context context, @NonNull File file) {
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }
        String folderName = parentDir.getName();
        int[] expectedSize = EXPECTED_SIZES.get(folderName);
        if (expectedSize == null) {
            return;
        }

        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg")
                && !fileName.endsWith(".jpeg") && !fileName.endsWith(".webp")) {
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
                        folderName + "/" + file.getName(),
                        expectedWidth, expectedHeight,
                        width, height);
                // Report using context location for the file
                context.report(
                        new Incident(
                                ISSUE,
                                message,
                                context.getLocation(file),
                                null));
            }
        } catch (IOException e) {
            // Can't read the image, skip
        }
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
        // We handle XML elements for adaptive icons or other icon-related XML
        return Collections.singletonList("adaptive-icon");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check adaptive icon XML files for size issues
        File file = context.file;
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }
        String folderName = parentDir.getName();
        // Adaptive icons in mipmap folders - note size constraints apply
        if (!folderName.startsWith("mipmap") && !folderName.startsWith("drawable")) {
            return;
        }
        // For adaptive icons we just track them; actual size checking is on the rendered output
        // which is beyond static analysis scope here, so we skip detailed checks.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for icon size detection
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No specific method-level icon size checks needed
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No specific call expression icon size checks needed
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No specific name reference icon size checks needed
            }
        };
    }
}