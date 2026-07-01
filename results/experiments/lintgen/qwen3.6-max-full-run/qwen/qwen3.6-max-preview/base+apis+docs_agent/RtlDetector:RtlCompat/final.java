package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must also specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (!SdkConstants.ANDROID_URI.equals(namespace)) {
            return;
        }

        String name = attribute.getLocalName();
        String value = attribute.getValue();
        Element element = attribute.getOwnerElement();

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "gravity");
            String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
            if (gravity.isEmpty() && layoutGravity.isEmpty()) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `textAlignment`, also specify `android:gravity` or `android:layout_gravity` for compatibility with API < 17");
            }
            return;
        }

        if ("gravity".equals(name) || "layout_gravity".equals(name)) {
            boolean hasStart = value.contains("start");
            boolean hasEnd = value.contains("end");
            boolean hasLeft = value.contains("left");
            boolean hasRight = value.contains("right");

            if ((hasStart && !hasLeft) || (hasEnd && !hasRight)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `start` or `end` in `" + name + "`, also specify `left` or `right` for compatibility with API < 17");
            }
            return;
        }

        String oldAttr = getOldAttribute(name);
        if (oldAttr != null) {
            String oldVal = element.getAttributeNS(SdkConstants.ANDROID_URI, oldAttr);
            if (oldVal.isEmpty()) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `" + name + "`, also specify `android:" + oldAttr + "` for compatibility with API < 17");
            }
        }
    }

    private String getOldAttribute(String name) {
        if (!isRtlAttributeName(name)) {
            return null;
        }
        boolean hasLayoutPrefix = name.startsWith("layout_");
        String baseName = hasLayoutPrefix ? name.substring(7) : name;
        String oldBase = convertNewToOld(baseName);
        return hasLayoutPrefix ? "layout_" + oldBase : oldBase;
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File folder) {
        String name = folder.getName();
        int index = name.lastIndexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }
}