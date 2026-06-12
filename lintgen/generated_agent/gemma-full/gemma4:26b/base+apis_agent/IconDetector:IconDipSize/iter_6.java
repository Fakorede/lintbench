package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconSizeMismatch",
            "Icon size mismatch across densities",
            "The icon %s has different density-independent pixel (dp) sizes in different density folders.",
            com.android.tools.lint.detector.api.Category.CORRECTNESS,
            5,
            Severity.ERROR,
            Implementation.create(IconDetector.class, Scope.RESOURCE_FILES)
    );

    private static final List<String> TARGET_ATTRIBUTES = Arrays.asList("android:src", "android:background", "android:tint", "android:drawable");
    private static final String[] DENSITIES = {"", "-hdpi", "-xhdpi", "-xxhdpi", "-xxxhdpi"};
    private static final double[] SCALES = {1.0, 1.5, 2.0, 3.0, 4.0};
    private static final String[] EXTENSIONS = {".png", ".webp"};

    @Override
    public List<String> getApplicableAttributes() {
        return TARGET_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String[] parts = value.split("/");
        if (parts.length < 2) {
            return;
        }

        String type = parts[0].substring(1); // remove '@'
        String resourceName = parts[1];

        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        File resDir = findResDir(context);
        if (resDir == null) {
            return;
        }

        Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();

        for (int i = 0; i < DENSITIES.length; i++) {
            String densitySuffix = DENSITIES[i];
            String folderName = type + densitySuffix;
            File folder = new File(resDir, folderName);

            if (folder.exists() && folder.isDirectory()) {
                for (String ext : EXTENSIONS) {
                    File imageFile = new File(folder, resourceName + ext);
                    if (imageFile.exists()) {
                        try {
                            BufferedImage img = ImageIO.read(imageFile);
                            if (img != null) {
                                dimensionsBy            ByDensity.put(densitySuffix, 
                                        new DimensionInfo(img.getWidth() / SCALES[i], img.getHeight() / SCALES[i]));
                            }
                        } catch (Exception ignored) {
                        }
                        break; // Found the image for this density
                    }
                }
            }
        }

        if (dimensionsByDensity.size() > 1) {
            DimensionInfo base = null;
            for (DimensionInfo current : dimensionsByDensity.values()) {
                if (base == null) {
                    base = current;
                } else {
                    if (Math.abs(current.widthDp - base.widthDp) > 0.1 || 
                        Math.abs(current.heightDp - base.heightDp) > 0.1) {
                        context.report(ISSUE, attribute, context.getLocation(attribute), 
                                String.format("The icon %s has different dp sizes across densities", resourceName));
                        break;
                    }
                }
            }
        }
    }

    private File findResDir(XmlContext context) {
        for (File file : context.getFiles()) {
            String path = file.getAbsolutePath();
            int resIndex = path.lastIndexOf("/res/");
            if (resIndex != -1) {
                return new File(path.substring(0, resIndex + 4));
            }
        }
        return null;
    }

    private static class DimensionInfo {
        final double widthDp;
        final double heightDp;

        DimensionInfo(double widthDp, double heightDp) {
            this.widthDp = widthDp;
            this.heightDp = heightDp;
        }
    }
}