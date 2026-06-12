package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconSizeMismatch",
            "Icon size mismatch across densities",
            "The icon %s has different density-independent pixel (dp) sizes in different density folders.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            Implementation.create(IconDetector.class, Scope.NONE)
    );

    private static final List<String> TARGET_ATTRIBUTES = Arrays.asList("android:src", "android:background", "android:drawable", "android:tint");
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final double[] SCALES = {1.0, 1.5, 2.0, 3.0, 4.0};
    private static final String[] EXTENSIONS = {".png", ".webp"};

    private static File cachedResDir = null;

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

        String content = value.substring(1);
        int slashIndex = content.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String type = content.substring(0, slashIndex);
        String resourceName = content.substring(slashIndex + 1);

        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        File resDir = findResDir(context);
        if (resDir == null) {
            return;
        }

        Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();

        for (int i = 0; i < DENSITIES.length; i++) {
            String density = DENSITIES[i];
            double scale = SCALES[i];
            File folder = new File(resDir, type + "-" + density);

            if (folder.exists() && folder.isDirectory()) {
                for (String ext : EXTENSIONS) {
                    File imageFile = new File(folder, resourceName + ext);
                    if (imageFile.exists()) {
                        try {
                            BufferedImage img = ImageIO.read(imageFile);
                            if (img != null) {
                                dimensionsByDensity.put(density, 
                                        new DimensionInfo(img.getWidth() / scale, img.getHeight() / scale));
                            }
                        } catch (IOException e) {
                            // Ignore errors reading images
                        }
                        break;
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
                    // Use a small epsilon for float comparison to avoid false positives from rounding
                    if (Math.abs(current.widthDp - base.widthDp) > 0.5 || 
                        Math.abs(current.heightDp - base.heightDp) > 0.5) {
                        context.report(ISSUE, attribute, context.getLocation(attribute), 
                                String.format("The icon %s has different density-independent pixel (dp) sizes in different density folders.", resourceName));
                        break;
                    }
                }
            }
        }
    }

    private File findResDir(XmlContext context) {
        if (cachedResDir != null) return cachedResDir;
        File root = context.getProject().getRootDir();
        cachedResDir = searchForRes(root, 0);
        return cachedResDir;
    }

    private File searchForRes(File dir, int depth) {
        if (depth > 5 || !dir.isDirectory()) return null;

        File resFolder = new File(dir, "res");
        if (resFolder.exists() && resFolder.isDirectory()) {
            // Check if it's a valid Android resource folder by looking for mdpi
            if (new File(resFolder, "drawable-mdpi").exists() || new File(resFolder, "mipmap-mdpi").exists()) {
                return resFolder;
            }
        }

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                // Skip common non-source directories to speed up search
                if (child.isDirectory() && !child.getName().equals("build") && !child.getName().equals(".git")) {
                    File found = searchForRes(child, depth + 1);
                    if (found != null) return found;
                }
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