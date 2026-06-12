package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconSizeMismatch",
            "Icon size mismatch across densities",
            "The icon %s has different density-independent pixel (dp) sizes in different density folders. " +
            "Ensure all versions of the icon have consistent dimensions.",
            Issue.Severity.ERROR,
            true,
            null
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkAttribute(context, element, "android:src");
        checkAttribute(context, element, "android:drawable");
    }

    private void checkAttribute(XmlContext context, Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value != null && value.startsWith("@drawable/")) {
            String resourceName = value.substring("@drawable/".length());
            validateResourceConsistency(context, element, resourceName);
        }
    }

    private void validateResourceConsistency(XmlContext context, Element element, String resourceName) {
        File rootDir = context.getProject().getRootDir();
        if (rootDir == null) return;

        Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();
        searchForDrawableResources(rootDir, resourceName, dimensionsByDensity);

        if (dimensionsByDensity.size() > 1) {
            List<DimensionInfo> infos = new ArrayList<>(dimensionsByDensity.values());
            DimensionInfo base = infos.get(0);
            for (int i = 1; i < infos.size(); i++) {
                DimensionInfo current = infos.get(i);
                // Allow a small tolerance for rounding errors in scaling
                if (Math.abs(current.widthDp - base.widthDp) > 1.0 || 
                    Math.abs(current.heightDp - base.heightDp) > 1.0) {
                    context.report(ISSUE, element, null, "@drawable/" + resourceName);
                    break;
                }
            }
        }
    }

    private void searchForDrawableResources(File dir, String resourceName, Map<String, DimensionInfo> dimensionsByDensity) {
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if (name.startsWith("drawable-")) {
                    checkFolderForResource(file, resourceName, dimensionsByDensity);
                } else if (name.equals("res") || name.equals("src") || name.contains("app")) {
                    searchForDrawableResources(file, resourceName, dimensionsByDensity);
                }
            }
        }
    }

    private void checkFolderForResource(File folder, String resourceName, Map<String, DimensionInfo> dimensionsByDensity) {
        double scale = getDensityScale(folder.getName());
        if (scale < 0) return;

        String[] extensions = {".png", ".webp"};
        for (String ext : extensions) {
            File imgFile = new File(folder, resourceName + ext);
            if (imgFile.exists()) {
                try {
                    BufferedImage img = ImageIO.read(imgFile);
                    if (img != null) {
                        dimensionsByDensity.put(folder.getName(), 
                            new DimensionInfo(img.getWidth() / scale, img.getHeight() / scale));
                    }
                } catch (IOException ignored) {
                }
                break;
            }
        }
    }

    private double getDensityScale(String folderName) {
        if (folderName.contains("mdpi")) return 1.0;
        if (folderName.contains("hdpi")) return 1.5;
        if (folderName.contains("xhdpi")) return 2.0;
        if (folderName.contains("xxhdpi")) return 3.0;
        if (folderName.contains("xxxhdpi")) return 4.0;
        return -1.0;
    }

    private static class DimensionInfo {
        final double widthDp;
        final double heightDp;

        DimensionInfo(double widthDp, double doubleHeight) {
            this.widthDp = widthDp;
            this.heightDp = doubleHeight;
        }
    }
}