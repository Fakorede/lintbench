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
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue SYMMETRY = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "If you specify padding or margin on the left side of a layout, you should " +
        "probably also specify padding on the right side (and vice versa) for " +
        "right-to-left layout symmetry.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue USE_START_END = Issue.create(
        "RtlHardcoded",
        "Using 'left'/'right' instead of 'start'/'end'",
        "To support right-to-left layouts, you should use start/end instead of left/right.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue COMPAT = Issue.create(
        "RtlCompat",
        "Right-to-left compatibilities",
        "To support right-to-left layouts, you should use start/end instead of left/right.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "To support right-to-left layouts, you should enable RTL support.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
        // Layouts
        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,            SdkConstants.ATTR_LAYOUT_MARGIN_START,
        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,           SdkConstants.ATTR_LAYOUT_MARGIN_END,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,      SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,     SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
        SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,             SdkConstants.ATTR_LAYOUT_ALIGN_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,            SdkConstants.ATTR_LAYOUT_ALIGN_END,
        SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,             SdkConstants.ATTR_LAYOUT_TO_START_OF,
        SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,            SdkConstants.ATTR_LAYOUT_TO_END_OF,

        // Views
        SdkConstants.ATTR_PADDING_LEFT,                  SdkConstants.ATTR_PADDING_START,
        SdkConstants.ATTR_PADDING_RIGHT,                 SdkConstants.ATTR_PADDING_END,
        SdkConstants.ATTR_DRAWABLE_LEFT,                 SdkConstants.ATTR_DRAWABLE_START,
        SdkConstants.ATTR_DRAWABLE_RIGHT,                SdkConstants.ATTR_DRAWABLE_END
    };

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN);
    }

    private void checkSymmetry(XmlContext context, Element element, String left, String right, String bound) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, left);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, right);
        if (hasLeft != hasRight) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, bound)) {
                return;
            }
            String attribute = hasLeft ? left : right;
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attribute);
            String message = hasLeft
                    ? String.format("When you define `%1$s` you should also define `%2$s` for symmetry", left, right)
                    : String.format("When you define `%1$s` you should also define `%2$s` for symmetry", right, left);
            context.report(SYMMETRY, attr, context.getLocation(attr), message);
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Right";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Start";
        } else if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    public static int getFolderVersion(File file) {
        if (file == null) {
            return -1;
        }
        String name = file.getName();
        int index = name.lastIndexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        File parent = file.getParentFile();
        if (parent != null) {
            name = parent.getName();
            index = name.lastIndexOf("-v");
            if (index != -1) {
                try {
                    return Integer.parseInt(name.substring(index + 2));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return -1;
    }
}