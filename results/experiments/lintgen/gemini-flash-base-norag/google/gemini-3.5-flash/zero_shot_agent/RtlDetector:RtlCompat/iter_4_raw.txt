package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    public static final String[] ATTRIBUTES = new String[] {
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd"
    };

    public static final Implementation IMPLEMENTATION = new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue COMPAT = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue ENABLED = Issue.create(
            "RtlEnabled",
            "Using RTL attributes without enabling RTL support",
            "To use RTL attributes, you must also enable RTL support by setting " +
            "`android:supportsRtl=\"true\"` in the `AndroidManifest.xml` file.",
            Category.RTL,
            3,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on one side (e.g. left), you should " +
            "usually also specify it on the other side (e.g. right) to ensure " +
            "symmetric behavior.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue USE_START = Issue.create(
            "RtlHardcoded",
            "Using left/right instead of start/end",
            "Using `left`/`right` instead of `start`/`end` attributes can prevent " +
            "your layout from being properly mirrored in right-to-left layouts.",
            Category.RTL,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue HARDCODED = USE_START;
    public static final Issue ISSUE = COMPAT;

    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";
    private static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT = "layout_alignParentLeft";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT = "layout_alignParentRight";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_START = "layout_alignParentStart";
    private static final String ATTR_LAYOUT_ALIGN_PARENT_END = "layout_alignParentEnd";
    private static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";
    private static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";
    private static final String ATTR_LAYOUT_TO_START_OF = "layout_toStartOf";
    private static final String ATTR_LAYOUT_TO_END_OF = "layout_toEndOf";
    private static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";
    private static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";
    private static final String ATTR_LAYOUT_ALIGN_START = "layout_alignStart";
    private static final String ATTR_LAYOUT_ALIGN_END = "layout_alignEnd";
    private static final String ATTR_DRAWABLE_LEFT = "drawableLeft";
    private static final String ATTR_DRAWABLE_RIGHT = "drawableRight";
    private static final String ATTR_DRAWABLE_START = "drawableStart";
    private static final String ATTR_DRAWABLE_END = "drawableEnd";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

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
        if (attribute == null) return null;
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Right";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Start";
        }
        if (attribute.contains("Left")) {
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

    public static boolean isRtlAttributeName(String attribute) {
        if (attribute == null) return false;
        return attribute.contains("Start") || attribute.contains("End");
    }

    private static boolean isOldAttribute(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(attribute)) {
                return true;
            }
        }
        return false;
    }

    public static int getFolderVersion(File file) {
        if (file == null) {
            return -1;
        }
        String path = file.getPath();
        String[] segments = path.split("[/\\\\]");
        for (String segment : segments) {
            if (segment.startsWith("values-") || segment.startsWith("layout-") || segment.contains("-v")) {
                int index = segment.indexOf("-v");
                if (index != -1 && index + 2 < segment.length()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = index + 2; i < segment.length(); i++) {
                        char c = segment.charAt(i);
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
            }
        }
        return -1;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            ATTR_TEXT_ALIGNMENT,
            ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT,
            ATTR_PADDING_START, ATTR_PADDING_END,
            ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT,
            ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END,
            ATTR_LAYOUT_ALIGN_PARENT_LEFT, ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
            ATTR_LAYOUT_ALIGN_PARENT_START, ATTR_LAYOUT_ALIGN_PARENT_END,
            ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_TO_RIGHT_OF,
            ATTR_LAYOUT_TO_START_OF, ATTR_LAYOUT_TO_END_OF,
            ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_ALIGN_RIGHT,
            ATTR_LAYOUT_ALIGN_START, ATTR_LAYOUT_ALIGN_END,
            ATTR_DRAWABLE_LEFT, ATTR_DRAWABLE_RIGHT,
            ATTR_DRAWABLE_START, ATTR_DRAWABLE_END,
            ATTR_GRAVITY, ATTR_LAYOUT_GRAVITY
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();

        int folderVersion = getFolderVersion(context.file);
        int minSdk = 1;
        if (context.getProject() != null) {
            minSdk = context.getProject().getMinSdk();
        }

        // 1. RtlCompat for textAlignment
        if (ATTR_TEXT_ALIGNMENT.equals(name)) {
            if (minSdk < 17 && folderVersion < 17) {
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_GRAVITY) &&
                        !element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
                    context.report(
                            COMPAT,
                            attribute,
                            context.getLocation(attribute),
                            "To support older versions than API 17, you must also specify a gravity or layout_gravity attribute"
                    );
                }
            }
        }

        // 2. RtlEnabled check
        if (isRtlAttributeName(name)) {
            if (context.getProject() != null && !context.getProject().isRtlSupported()) {
                int targetSdk = context.getProject().getTargetSdk();
                if (targetSdk >= 17) {
                    context.report(
                            ENABLED,
                            attribute,
                            context.getLocation(attribute),
                            "To use RTL attributes, you must also enable RTL support by setting `android:supportsRtl=\"true\"` in the `AndroidManifest.xml` file"
                    );
                }
            }
        }

        // 3. RtlCompat and RtlHardcoded for Left/Right vs Start/End
        boolean isOld = isOldAttribute(name);
        boolean isNew = isRtlAttributeName(name);

        if (isNew) {
            if (minSdk < 17 && folderVersion < 17) {
                String oldAttr = convertNewToOld(name);
                if (oldAttr != null && !oldAttr.equals(name)) {
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, oldAttr)) {
                        context.report(
                                COMPAT,
                                attribute,
                                context.getLocation(attribute),
                                String.format("To support older versions than API 17, you must also specify `%s`", oldAttr)
                        );
                    }
                }
            }
        } else if (isOld) {
            if (folderVersion >= 17 || folderVersion == -1) {
                String newAttr = convertOldToNew(name);
                if (newAttr != null && !newAttr.equals(name)) {
                    if (minSdk >= 17) {
                        context.report(
                                HARDCODED,
                                attribute,
                                context.getLocation(attribute),
                                String.format("Use `%s` instead of `%s` to ensure correct behavior in right-to-left layouts", newAttr, name)
                        );
                    } else {
                        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, newAttr)) {
                            context.report(
                                    COMPAT,
                                    attribute,
                                    context.getLocation(attribute),
                                    String.format("Should also provide `%s` for compatibility with API 17+", newAttr)
                            );
                        }
                    }
                }
            }
        }

        // 4. Symmetry check
        if (name.equals(ATTR_PADDING_LEFT) || name.equals(ATTR_PADDING_RIGHT) ||
            name.equals(ATTR_PADDING_START) || name.equals(ATTR_PADDING_END) ||
            name.equals(ATTR_LAYOUT_MARGIN_LEFT) || name.equals(ATTR_LAYOUT_MARGIN_RIGHT) ||
            name.equals(ATTR_LAYOUT_MARGIN_START) || name.equals(ATTR_LAYOUT_MARGIN_END)) {
            checkSymmetryForAttribute(context, attribute, name, element);
        }
    }

    private void checkSymmetryForAttribute(XmlContext context, Attr attribute, String name, Element element) {
        if (name.equals(ATTR_PADDING_LEFT) || name.equals(ATTR_PADDING_RIGHT)) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "padding")) {
                boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_PADDING_LEFT);
                boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_PADDING_RIGHT);
                if (hasLeft != hasRight) {
                    context.report(
                            SYMMETRY,
                            attribute,
                            context.getLocation(attribute),
                            "If you specify padding on one side, you should also specify it on the other side to ensure symmetric behavior"
                    );
                }
            }
        } else if (name.equals(ATTR_PADDING_START) || name.equals(ATTR_PADDING_END)) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "padding")) {
                boolean hasStart = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_PADDING_START);
                boolean hasEnd = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_PADDING_END);
                if (hasStart != hasEnd) {
                    context.report(
                            SYMMETRY,
                            attribute,
                            context.getLocation(attribute),
                            "If you specify padding on one side, you should also specify it on the other side to ensure symmetric behavior"
                    );
                }
            }
        } else if (name.equals(ATTR_LAYOUT_MARGIN_LEFT) || name.equals(ATTR_LAYOUT_MARGIN_RIGHT)) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_margin")) {
                boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT);
                boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT);
                if (hasLeft != hasRight) {
                    context.report(
                            SYMMETRY,
                            attribute,
                            context.getLocation(attribute),
                            "If you specify margin on one side, you should also specify it on the other side to ensure symmetric behavior"
                    );
                }
            }
        } else if (name.equals(ATTR_LAYOUT_MARGIN_START) || name.equals(ATTR_LAYOUT_MARGIN_END)) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_margin")) {
                boolean hasStart = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_MARGIN_START);
                boolean hasEnd = element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_LAYOUT_MARGIN_END);
                if (hasStart != hasEnd) {
                    context.report(
                            SYMMETRY,
                            attribute,
                            context.getLocation(attribute),
                            "If you specify margin on one side, you should also specify it on the other side to ensure symmetric behavior"
                    );
                }
            }
        }
    }
}