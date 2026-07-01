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

    public static final String[] ATTRIBUTES = new String[] {
        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_START,
        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_END,
        SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_START,
        SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING_END,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
        SdkConstants.ATTR_LAYOUT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_TO_START_OF,
        SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_ALIGN_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_END,
        SdkConstants.ATTR_DRAWABLE_LEFT, SdkConstants.ATTR_DRAWABLE_START,
        SdkConstants.ATTR_DRAWABLE_RIGHT, SdkConstants.ATTR_DRAWABLE_END
    };

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
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        }
        if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        }
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        }
        if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static int getFolderVersion(File file) {
        if (file == null) {
            return -1;
        }
        int version = getFolderVersion(file.getName());
        if (version != -1) {
            return version;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return getFolderVersion(parent.getName());
        }
        return -1;
    }

    private static int getFolderVersion(String folderName) {
        if (folderName == null) {
            return -1;
        }
        int index = folderName.lastIndexOf("-v");
        if (index != -1 && index + 2 < folderName.length()) {
            String versionString = folderName.substring(index + 2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < versionString.length(); i++) {
                char c = versionString.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                try {
                    return Integer.parseInt(sb.toString());
                } catch (NumberFormatException e) {
                    // ignore
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
    public void visitElement(XmlContext context, Element element) {
        boolean isLayout = context.getResourceFolderType() == com.android.resources.ResourceFolderType.LAYOUT;
        if (!isLayout) {
            return;
        }

        checkSymmetry(context, element);
        checkRtlAttributes(context, element);
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
                    String.format("To support right-to-left layouts, when you define %1$s you should also define %2$s", "android:" + present, "android:" + missing)
            );
        }
    }

    private void checkRtlAttributes(XmlContext context, Element element) {
        int minSdk = 1;
        int targetSdk = 1;
        try {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }
        try {
            targetSdk = context.getProject().getTargetSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }

        int folderVersion = getFolderVersion(context.file);
        int effectiveMinSdk = Math.max(minSdk, folderVersion);

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String namespace = attribute.getNamespaceURI();
            if (!SdkConstants.ANDROID_URI.equals(namespace)) {
                continue;
            }
            String localName = attribute.getLocalName();
            if (localName == null) {
                continue;
            }

            String newAttr = convertOldToNew(localName);
            if (!newAttr.equals(localName)) {
                if (targetSdk >= 17) {
                    if (effectiveMinSdk >= 17) {
                        context.report(
                            USE_START_END,
                            attribute,
                            context.getLocation(attribute),
                            String.format("Use `android:%1$s` instead of `android:%2$s` to ensure correct behavior on right-to-left layouts", newAttr, localName)
                        );
                    } else {
                        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, newAttr)) {
                            context.report(
                                COMPATIBILITY,
                                attribute,
                                context.getLocation(attribute),
                                String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "android:" + newAttr)
                            );
                        }
                    }
                }
            }

            String oldAttr = convertNewToOld(localName);
            if (!oldAttr.equals(localName)) {
                boolean supportRtl = false;
                try {
                    supportRtl = context.getProject().getSupportRtl();
                } catch (Throwable t) {
                    // ignore
                }
                if (targetSdk >= 17 && !supportRtl) {
                    context.report(
                        ENABLED,
                        attribute,
                        context.getLocation(attribute),
                        "To use RTL attributes, you must set android:supportsRtl=\"true\" in the manifest"
                    );
                }

                if (targetSdk >= 17 && effectiveMinSdk < 17) {
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, oldAttr)) {
                        context.report(
                            COMPATIBILITY,
                            attribute,
                            context.getLocation(attribute),
                            String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "android:" + oldAttr)
                        );
                    }
                }
            }

            if ("gravity".equals(localName) || "layout_gravity".equals(localName)) {
                String value = attribute.getNodeValue();
                if (value != null) {
                    boolean hasLeft = value.contains("left");
                    boolean hasRight = value.contains("right");
                    boolean hasStart = value.contains("start");
                    boolean hasEnd = value.contains("end");

                    if (targetSdk >= 17) {
                        if (effectiveMinSdk >= 17) {
                            if (hasLeft) {
                                context.report(USE_START_END, attribute, context.getLocation(attribute),
                                    "Use `start` instead of `left` to ensure correct behavior on right-to-left layouts");
                            }
                            if (hasRight) {
                                context.report(USE_START_END, attribute, context.getLocation(attribute),
                                    "Use `end` instead of `right` to ensure correct behavior on right-to-left layouts");
                            }
                        } else {
                            if (hasLeft && !hasStart) {
                                context.report(COMPATIBILITY, attribute, context.getLocation(attribute),
                                    String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "start"));
                            }
                            if (hasRight && !hasEnd) {
                                context.report(COMPATIBILITY, attribute, context.getLocation(attribute),
                                    String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "end"));
                            }
                            if (hasStart && !hasLeft) {
                                context.report(COMPATIBILITY, attribute, context.getLocation(attribute),
                                    String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "left"));
                            }
                            if (hasEnd && !hasRight) {
                                context.report(COMPATIBILITY, attribute, context.getLocation(attribute),
                                    String.format("To support older versions than API 17 (your minSdkVersion is %1$d), you should also define %2$s", minSdk, "right"));
                            }
                        }
                    }
                }
            }
        }
    }
}