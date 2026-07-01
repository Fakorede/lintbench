package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.io.File;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtlDetector extends LayoutDetector {

    public static final Issue USE_START_END = Issue.create(
            "RtlHardcoded",
            "Using 'left'/'right' instead of 'start'/'end' attributes",
            "To support right-to-left layouts on L and higher, you should use start/end attributes.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue COMPATIBILITY = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility",
            "API 17 adds start/end attributes. If you support older APIs, you must also keep the left/right attributes.",
            Category.I18N,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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

    public static final Issue ENABLED = Issue.create(
            "RtlEnabled",
            "Using RTL attributes without enabling RTL support",
            "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in the manifest.",
            Category.I18N,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue ISSUE = SYMMETRY;

    public static final String[] ATTRIBUTES = new String[] {
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd",
        "alignParentLeft", "alignParentStart",
        "alignParentRight", "alignParentEnd",
        "toLeftOf", "toStartOf",
        "toRightOf", "toEndOf",
        "alignLeft", "alignStart",
        "alignRight", "alignEnd",
        "marginLeft", "marginStart",
        "marginRight", "marginEnd"
    };

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        checkSymmetry(context, element);

        int minSdk = context.getProject().getMinSdk();
        int targetSdk = context.getProject().getTargetSdk();
        boolean supportsRtl = context.getProject().getSupportRtl();
        int folderVersion = getFolderVersion(context.file);

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String namespaceUri = attribute.getNamespaceURI();
            if (!SdkConstants.ANDROID_URI.equals(namespaceUri)) {
                continue;
            }
            String localName = attribute.getLocalName();
            if (localName == null) {
                continue;
            }

            if (isLeftRightAttribute(localName)) {
                String startEndEquivalent = convertOldToNew(localName);
                if (startEndEquivalent != null && !startEndEquivalent.equals(localName)) {
                    boolean hasStartEnd = element.hasAttributeNS(SdkConstants.ANDROID_URI, startEndEquivalent);
                    if (targetSdk >= 17) {
                        if (!hasStartEnd) {
                            if (minSdk >= 17 || folderVersion >= 17) {
                                String message = String.format("Use `android:%1$s` instead of `android:%2$s` to ensure correct behavior in right-to-left locales", startEndEquivalent, localName);
                                context.report(USE_START_END, attribute, context.getLocation(attribute), message);
                            } else {
                                String message = String.format("Consider adding `android:%1$s` to better support right-to-left layouts", startEndEquivalent);
                                context.report(USE_START_END, attribute, context.getLocation(attribute), message);
                            }
                        }
                    }
                }
            }

            if (isStartEndAttribute(localName)) {
                if (!supportsRtl && targetSdk >= 17) {
                    String message = "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in the `<application>` element of your manifest";
                    context.report(ENABLED, attribute, context.getLocation(attribute), message);
                }

                if (minSdk < 17 && folderVersion < 17) {
                    String leftRightEquivalent = convertNewToOld(localName);
                    if (leftRightEquivalent != null && !leftRightEquivalent.equals(localName)) {
                        boolean hasLeftRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftRightEquivalent);
                        if (!hasLeftRight) {
                            String message = String.format("To support older versions than API 17 (which is the minSdkVersion of this project) you should also add `android:%1$s` to accompany `android:%2$s`", leftRightEquivalent, localName);
                            context.report(COMPATIBILITY, attribute, context.getLocation(attribute), message);
                        }
                    }
                }
            }
        }
    }

    private void checkSymmetry(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN);
    }

    private void checkSymmetry(XmlContext context, Element element, String leftAttr, String rightAttr, String allAttr) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, leftAttr);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, rightAttr);
        boolean hasAll = allAttr != null && element.hasAttributeNS(SdkConstants.ANDROID_URI, allAttr);

        if (hasAll) {
            return;
        }

        if (hasLeft != hasRight) {
            String missing = hasLeft ? rightAttr : leftAttr;
            String present = hasLeft ? leftAttr : rightAttr;
            context.report(
                    SYMMETRY,
                    element,
                    context.getLocation(element.getAttributeNodeNS(SdkConstants.ANDROID_URI, present)),
                    String.format("To support right-to-left layouts, when you define `android:%1$s` you should also define `android:%2$s`", present, missing)
            );
        }
    }

    private static boolean isLeftRightAttribute(String name) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStartEndAttribute(String name) {
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRtlAttributeName(String name) {
        return isStartEndAttribute(name);
    }

    public static String convertOldToNew(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return ATTRIBUTES[i + 1];
            }
        }
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Start";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i + 1].equals(attribute)) {
                return ATTRIBUTES[i];
            }
        }
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Right";
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
        }
        return attribute;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int version = getVersionFromName(name);
        if (version != -1) {
            return version;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return getVersionFromName(parent.getName());
        }
        return -1;
    }

    private static int getVersionFromName(String name) {
        int index = name.indexOf("-v");
        if (index != -1) {
            int start = index + 2;
            int end = start;
            while (end < name.length() && Character.isDigit(name.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    return Integer.parseInt(name.substring(start, end));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return -1;
    }
}