package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.Issue.Severity.ERROR;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Attr;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

public class IconDetector implements com.android.tools.lint.detector.api.XmlScanner {

    private static final String ATTR_DRAWABLE = "android:drawable";
    private static final String ATTR_SRC = "android:src";
    private static final String ATTR_BACKGROUND = "android:background";

    private static final Map<String, Float> DENSITY_SCALES;

    static {
        Map<String, Float> scales = new HashMap<>();
        scales.put("mdpi", 1.0f);
        scales.put("hdpi", 1.5f);
        scales.put("xhdpi", 2.0f);
        scales.put("xxhdpi", 3.0f);
        scales.put("xxxhdpi", 4.0f);
        DENSITY_SCALES = Collections.unmodifiableMap(scales);
    }

    public static final Issue ISSUE = Issue.builder()
            .groupId("Android")
            .name("Incorrect Icon Size")
            .description("Launcher icons should follow the standard dimensions for each density to ensure they fit correctly in the platform.")
            .implementation(IconDetector.class, ERROR)
            .build();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_DRAWABLE, ATTR_SRC, ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        try {
            int resourceId = context.evaluateResource(attribute);
            if (resourceId == 0) {
                return;
            }

            File imageFile = context.getFiles().findResourceFile(resourceId);
            if (imageFile == null || !isImageFile(imageFile)) {
                return;
            }

            float scale = getScaleFromPath(imageFile.getAbsolutePath());
            int expectedSize = Math.round(48 * scale);

            // Using ImageReader to avoid loading the whole image into memory (more efficient for Lint)
            try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
                var readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    reader.dispose();

                    if (width != expectedSize || height != expectedSize) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                String.format("Icon size mismatch. Expected %d x %d for current density, but found %d x %</strong>%d.",
                                        expectedSize, expectedSize, width, height)
                        );
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors during linting
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".webp");
    }

    private float getScaleFromPath(String path) {
        for (Map.Entry<String, Float> entry : DENSITY_SCALES.entrySet()) {
            if (path.contains("-" + entry.getKey())) {
                return entry.getValue();
            }
        }
        return 1.0f;
    }
}