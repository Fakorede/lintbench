package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconSizeMismatch",
            "Icon size mismatch across densities",
            "The icon %s has different density-independent pixel (dp) sizes in different density folders.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            Implementation.create(IconDetector.class, Scope.JAVA_SOURCES)
    );

    private static final List<String> TARGET_ATTRIBUTES = Arrays.asList("android:src", "android:drawable", "android:background", "android:tint");
    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final double[] SCALES = {1.0, 1.5, 2.0, 3.0, 4.0};
    private static final String[] RESOURCE_TYPES = {"drawable", "mipmap"};

    @Override
    public List<String> getApplicableElements() {
        return null; // All elements
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        for (String attrName : TARGET_ATTRIBUTES) {
            String value = element.getAttribute(attrName);
            if (value != null && (value.startsWith("@drawable/") || value.startsWith("@mipmap/"))) {
                String resourceName = extractResourceName(value);
                if (resourceName != null) {
                    validateResourceConsistency(context, element, resourceName);
                }
            }
        }
    }

    private String extractResourceName(String value) {
        int slashIndex = value.indexOf("/");
        if (slashIndex != -1) {
            return value.substring(slashIndex + 1);
        }
        return null;
    }

    private void validateResourceConsistency(XmlContext context, Element element, String resourceName) {
        try {
            // Attempt to find the resource root by traversing up from the current XML file
            File xmlFile = new File(context.getEvaluationContext().getProject().getFiles().get(0).getAbsolutePath()); 
            // Note: In a real Lint environment, we'd use context.getEvaluationContext().getProject() to find the resource dir.
            // Since we are in a standalone detector, we traverse up from the current file path if available.
            // However, XmlContext doesn't directly give us the file of the element being visited easily without trickery.
            // We use the context's knowledge of the file.
            
            // For this implementation, we assume we can find the resource directory relative to the XML.
            // In Lint, we can often access the project structure.
            File resDir = findResourceDir(context);
            if (reselResDir(resDir)) {
                Map<String, DimensionInfo> dimensionsByDensity = new HashMap<>();

                for (String type : RESOURCE_TYPES) {
                    for (int i = 0; i < DENSITIES.length; i++) {
                        File folder;
                        if ("mdpi".equals(DENSITIES[i])) {
                            folder = new File(resDir, type);
                        } else {
                            folder = new File(resDir, type + "-" + DENSITIES[i]);
                        }

                        if (folder.exists() && folder.isDirectory()) {
                            File[] matches = folder.listFiles((dir, name) -> 
                                    name.startsWith(resourceName + ".") && 
                                    (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg")));

                            if (matches != null) {
                                for (File match : matches) {
                                    BufferedImage img = ImageIO.read(match);
                                    if (img != null) {
                                        dimensionsByDensity.put(type + "-" + DENSITIES[i], 
                                                new DimensionInfo(img.getWidth() / SCALES[i], img.getHeight() / SCALES[i]));
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
                            if (Math.abs(current.widthDp - base.widthDp) > 0.1 || 
                                Math.abs(current.heightDp - base.heightDp) > 0.1) {
                                context.report(ISSUE, element, null, String.format("The icon %s has different dp sizes across densities", resourceName));
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore errors in finding or reading files to prevent Lint from crashing
        }
    }

    private boolean reselResDir(File dir) {
        return dir != null && (dir.exists() && dir.isDirectory());
    }

    private File findResourceDir(XmlContext context) {
        // In a real lint check, we'd use the project structure. 
        // Here we attempt to locate 'res' by looking for common patterns in the project.
        try {
            // This is a heuristic approach for the purpose of this implementation.
            return new File(context.getEvaluationContext().getProject().getFiles().get(0).getParentFile(), "res");
        } catch (Exception e) {
            return null;
        }
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