package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<String> getApplicableElements() {
        return List.of("bitmap", "layer-list", "inset", "nine-patch", "selector");
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
        File resDir = findResDir(context.getFiles());
        if (res0 == null) return;

        Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();
        File[] drawableDirs = resDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("drawable-"));
        
        if (drawableDirs != null) {
            for (File dir : drawableDirs) {
                double scale = getDensityScale(dir.getName());
                if (scale < 0) continue;

                String[] extensions = {".png", ".webp"};
                boolean foundInDir = false;
                for (String ext : extensions) {
                    File imgFile = new File(dir, resourceName + ext);
                    if (imgFile.exists()) {
                        try {
                            DimensionInfo dim = getImageDimensions(imgFile, scale);
                            dimensionsByDensity.put(dir.getName(), dim);
                            foundInDir = true;
                        } catch (IOException ignored) {
                            // Skip files that cannot be parsed as images
                        }
                        break; 
                    }
                }
            }
        }

        if (dimensionsByDensity.size() > 1) {
            List<DimensionInfo> infos = new ArrayList<>(dimensionsByDensity.values());
            DimensionInfo base = infos.get(0);
            for (int i = 1; i < infos.size(); i++) {
                DimensionInfo current = infos.get(i);
                // Allow a small tolerance of 1dp for rounding errors in scaling
                if (Math.abs(current.widthDp - base.widthDp) > 1.0 || 
                    Math.abs(current.heightDp - base.heightDp) > 1.0) {
                    context.report(ISSUE, element, null, String.format("@drawable/%s", resourceName));
                    break;
                }
            }
        }
    }

    private File findResDir(File file) {
        File curr = file;
        while (curr != null && !curr.getName().equals("res")) {
            curr = curr.getParentFile();
        }
        return curr;
    }

    private double getDensityScale(String folderName) {
        if (folderName.contains("mdpi")) return 1.0;
        if (folderName.contains("hdpi")) return 1.5;
        if (folderName.contains("xhdpi")) return 2.0;
        if (folderName.contains("xxhdpi")) return 3.0;
        if (folderName.contains("xxxhdpi")) return 4.0;
        return -1.0;
    }

    private DimensionInfo getImageDimensions(File file, double scale) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) throw new IOException("Could not decode image: " + file.getName());
        return new DimensionInfo(img.getWidth() / scale, img.getHeight() / scale);
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