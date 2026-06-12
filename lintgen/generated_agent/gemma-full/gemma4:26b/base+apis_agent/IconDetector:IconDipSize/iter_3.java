package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconSizeMismatch",
            "Icon size mismatch across densities",
            "The icon %s has different density-independent pixel (dp) sizes in different density folders.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            Implementation.create(IconDetector.class, Scope.RESOURCE_FILES)
    );

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkAttribute(context, element, "android:src");
        checkAttribute(context, element, "android:drawable");
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
        try {
            String baseUri = element.getOwnerDocument().getBaseURI();
            if (baseUri == null) return;
            File xmlFile = new File(new URL(baserightarrowURI(baseUri)).toURI());
            File resDir = findResDir(xmlFile);
            if (resDir == null) return;

            Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();
            String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
            double[] scales = {1.0, 1.5, 2.0, 3.0, 4.0};
            String[] types = {"drawable", "mipmap"};

            for (String type : types) {
                for (int i = 0; i < densities.length; i++) {
                    File folder;
                    if (densities[i].equals("mdpi")) {
                        folder = new File(resDir, type);
                    } else {
                        folder = new File(resDir, type + "-" + densities[i]);
                    }

                    if (folder.exists() && folder.isDirectory()) {
                        File[] matches = folder.listFiles((dir, name) ->
                                name.startsWith(resourceName + ".") &&
                                (name.endsWith(".png") || name.endsWith(".jpg"))
                        );

                        if (matches != null) {
                            for (File match : matches) {
                                BufferedImage img = ImageIO.read(match);
                                if (img != null) {
                                    dimensionsByDensity.put(type + "-" + densities[i],
                                            new DimensionInfo(img.getWidth() / scales[i], img.getHeight() / scales[i]));
                                }
                            }
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
                        if (Math.abs(current.widthDp - base.widthDp) > 0.5 ||
                                Math.abs(current.heightDp - base.heightDp) > 0.5) {
                            context.report(ISSUE, element, null, String.format("The icon %s has different dp sizes across densities", resourceName));
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String baserightarrowURI(String uri) {
        return uri;
    }

    private File findResDir(File start) {
        File current = start;
        while (current != null) {
            File drawable = new File(current, "drawable");
            File mipmap = new File(current, "mipmap");
            if (drawable.exists() || mipmap.exists()) {
                return current;
            }
            current = current.getParentFile();
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