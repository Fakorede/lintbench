package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "If you specify padding or margin on the left side of a layout, you should " +
        "probably also specify padding on the right side (and vice versa) for " +
        "right-to-left layout symmetry.",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue SYMMETRY = ISSUE;

    public static final Issue USE_START_END = Issue.create(
        "RtlCompat",
        "Right-to-left compatibilities",
        "Placeholder",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue COMPAT = USE_START_END;

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "Placeholder",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue HARDCODED = Issue.create(
        "RtlHardcoded",
        "Using ltr attributes where RTL should be used",
        "Placeholder",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final String[] ATTRIBUTES = new String[] {
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,

        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,

        SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,
        SdkConstants.ATTR_LAYOUT_ALIGN_START,

        SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,
        SdkConstants.ATTR_LAYOUT_ALIGN_END,

        SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_TO_START_OF,

        SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_TO_END_OF,

        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
        SdkConstants.ATTR_LAYOUT_MARGIN_START,

        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
        SdkConstants.ATTR_LAYOUT_MARGIN_END,

        SdkConstants.ATTR_PADDING_LEFT,
        SdkConstants.ATTR_PADDING_START,

        SdkConstants.ATTR_PADDING_RIGHT,
        SdkConstants.ATTR_PADDING_END,

        SdkConstants.ATTR_DRAWABLE_LEFT,
        SdkConstants.ATTR_DRAWABLE_START,

        SdkConstants.ATTR_DRAWABLE_RIGHT,
        SdkConstants.ATTR_DRAWABLE_END
    };

    public static boolean isRtlAttributeName(String name) {
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return true;
            }
        }
        return false;
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
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i - 1];
            }
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            if (ATTRIBUTES[i].equals(attribute)) {
                int oppositeIndex;
                if (i % 4 < 2) {
                    oppositeIndex = i + 2;
                } else {
                    oppositeIndex = i - 2;
                }
                if (oppositeIndex >= 0 && oppositeIndex < ATTRIBUTES.length) {
                    return ATTRIBUTES[oppositeIndex];
                }
            }
        }
        return attribute;
    }

    public static int getFolderVersion(File folder) {
        if (folder == null) {
            return -1;
        }
        String name = folder.getName();
        for (String segment : name.split("-")) {
            if (segment.startsWith("v") && segment.length() > 1) {
                boolean allDigits = true;
                for (int i = 1; i < segment.length(); i++) {
                    if (!Character.isDigit(segment.charAt(i))) {
                        allDigits = false;
                        break;
                    }
                }
                if (allDigits) {
                    try {
                        return Integer.parseInt(segment.substring(1));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }
        return -1;
    }

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
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    private void checkSymmetry(XmlContext context, Element element, String left, String right) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, left);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, right);

        if (hasLeft != hasRight) {
            String present = hasLeft ? left : right;
            String missing = hasLeft ? right : left;
            context.report(
                ISSUE,
                element,
                context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, present)),
                "To support right-to-left layouts, when you define `" + present + "` you should also define `" + missing + "` for symmetry"
            );
        }
    }
}