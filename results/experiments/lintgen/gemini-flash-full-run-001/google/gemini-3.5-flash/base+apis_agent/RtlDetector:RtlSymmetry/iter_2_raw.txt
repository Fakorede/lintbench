package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final Issue SYMMETRY = Issue.create(
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

    public static final Issue USE_START_END = Issue.create(
            "RtlHardcoded",
            "Using 'left'/'right' instead of 'start'/'end' attributes",
            "To support right-to-left layouts, when targeting RTL you should use " +
            "start/end attributes instead of left/right attributes.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public static final Issue COMPAT = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "To support right-to-left layouts, when targeting RTL you should use " +
            "both start/end and left/right attributes.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public static final Issue ENABLED = Issue.create(
            "RtlEnabled",
            "Using RTL attributes without enabling RTL support",
            "To use RTL attributes, you must enable RTL support in the manifest.",
            Category.RTL,
            3,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public static final String[] ATTRIBUTES = new String[] {
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd"
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
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        int targetSdk = context.getProject().getTargetSdkVersion().getFeatureLevel();
        boolean rtlSupported = context.getProject().isRtlSupported();

        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String oldAttr = ATTRIBUTES[i];
            String newAttr = ATTRIBUTES[i + 1];

            boolean hasOld = element.hasAttributeNS(SdkConstants.ANDROID_URI, oldAttr);
            boolean hasNew = element.hasAttributeNS(SdkConstants.ANDROID_URI, newAttr);

            if (hasOld && !hasNew) {
                if (targetSdk >= 17) {
                    Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, oldAttr);
                    if (attribute != null) {
                        context.report(USE_START_END, attribute, context.getLocation(attribute),
                                String.format("Consider replacing `%1$s` with `%2$s` to better support right-to-left layouts", oldAttr, newAttr));
                    }
                }
            }

            if (hasNew && !hasOld) {
                if (minSdk < 17) {
                    Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, newAttr);
                    if (attribute != null) {
                        context.report(COMPAT, attribute, context.getLocation(attribute),
                                String.format("To support API levels < 17, you should also define `%1$s` when you define `%2$s`", oldAttr, newAttr));
                    }
                }
            }

            if (hasNew && !rtlSupported) {
                Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, newAttr);
                if (attribute != null) {
                    context.report(ENABLED, attribute, context.getLocation(attribute),
                            String.format("To use `%1$s`, you must enable RTL support by setting `android:supportsRtl=\"true\"` in the manifest", newAttr));
                }
            }
        }
    }

    private void checkSymmetry(XmlContext context, Element element, String attr1, String attr2) {
        boolean hasAttr1 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr1);
        boolean hasAttr2 = element.hasAttributeNS(SdkConstants.ANDROID_URI, attr2);
        if (hasAttr1 && !hasAttr2) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr1);
            if (attribute != null) {
                context.report(SYMMETRY, attribute, context.getLocation(attribute),
                        String.format("To support right-to-left layouts, when you define `%1$s` you should also define `%2$s`", attr1, attr2));
            }
        } else if (hasAttr2 && !hasAttr1) {
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attr2);
            if (attribute != null) {
                context.report(SYMMETRY, attribute, context.getLocation(attribute),
                        String.format("To support right-to-left layouts, when you define `%1$s` you should also define `%2$s`", attr2, attr1));
            }
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End") || name.contains("StartOf") || name.contains("EndOf");
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
        } else if (attribute.contains("LeftOf")) {
            return attribute.replace("LeftOf", "RightOf");
        } else if (attribute.contains("RightOf")) {
            return attribute.replace("RightOf", "LeftOf");
        } else if (attribute.contains("StartOf")) {
            return attribute.replace("StartOf", "EndOf");
        } else if (attribute.contains("EndOf")) {
            return attribute.replace("EndOf", "StartOf");
        }
        return attribute;
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Start";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.contains("LeftOf")) {
            return attribute.replace("LeftOf", "StartOf");
        } else if (attribute.contains("RightOf")) {
            return attribute.replace("RightOf", "EndOf");
        } else if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Right";
        } else if (attribute.contains("StartOf")) {
            return attribute.replace("StartOf", "LeftOf");
        } else if (attribute.contains("EndOf")) {
            return attribute.replace("EndOf", "RightOf");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return attribute;
    }

    public static int getFolderVersion(File file) {
        String parentName = file.getName();
        int index = parentName.lastIndexOf("-v");
        if (index != -1) {
            String versionString = parentName.substring(index + 2);
            try {
                return Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}