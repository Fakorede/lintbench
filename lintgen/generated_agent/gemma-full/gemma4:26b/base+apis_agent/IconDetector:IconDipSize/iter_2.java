package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
            "The icon %s has different density-independent pixel (dp) sizes in different density folders.",
            Severity.ERROR,
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
        checkcheckAttribute(context, element, "android:drawable");
        checkAttribute(context, element, "android:background");
    }

    private void checkAttribute(XmlContext context, Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value != null && (value.startsWith("@drawable/") || value.startsWith("@mipmap/"))) {
            int slashIndex = value.indexOf("/");
            if (slashIndex != -1) {
                String resourceName = value.substring(slashIndex + 1);
                validateResourceConsistency(context, element, resourceName);
            }
        }
    }

    private void validateResourceConsistency(XmlContext context, Element element, String resourceName) {
        Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();

        for (File file : context.getFiles()) {
            String fileName = file.getName();
            String parentName = file.getParentFile().getName();

            if ((parentName.startsWith("drawable-") || parentName.startsWith("mipmap-")) &&
                (fileName.startsWith(resourceName + "."))) {

                double scale = getDensityScale(parentName);
                if (scale > 0) {
                    try {
                        BufferedImage img = ImageIO.read(file);
                        if (img != null) {
                            dimensionsByDensity.put(parentName,
                                    new DimensionInfo(img.getWidth() / scale, img.getHeight() / scale));
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        if (dimensionsByDensity.size() > 1) {
            List<DimensionInfo> infos = new ArrayList<>(dimensionsByDensity.values());
            DimensionInfo base = infos.get(0);
            for (int i = 1; i < infos.size(); i++) {
                DimensionInfo current = infos.get(i);
                if (Math.abs(current.widthDp - base.widthDp) > 0.5 ||
                    Math.abs(current.heightDp - base.heightDp) > 0.5) {
                    context.report(ISSUE, element, null, String.format("The icon %s has different dp sizes across densities", resourceName));
                    break;
                }
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

        DimensionInfo(double widthDp, double heightDp) {
            this.widthDp = widthDp;
            this.heightDp = heightDp;
        }
    }
}