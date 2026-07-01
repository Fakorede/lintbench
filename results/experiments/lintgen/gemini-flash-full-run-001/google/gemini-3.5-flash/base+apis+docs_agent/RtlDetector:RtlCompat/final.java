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
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue COMPAT = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
        "if you are supporting older versions than API 17, you must **also** specify a " +
        "gravity or layout_gravity attribute, since older platforms will ignore the " +
        "`textAlignment` attribute.",
        Category.RTL,
        6,
        Severity.ERROR,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue ISSUE = COMPAT;

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "Using RTL attributes without enabling RTL support",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue SYMMETRY = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "Padding and margin symmetry",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue USE_START_END = Issue.create(
        "RtlHardcoded",
        "Using ltr attributes where RTL should be used",
        "Using ltr attributes where RTL should be used",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd"
    };

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static boolean isLtrAttributeName(String name) {
        return name.contains("Left") || name.contains("Right");
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
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

    public static int getFolderVersion(File file) {
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

    public static int getFolderVersion(String folderName) {
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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            "textAlignment",
            "gravity",
            "layout_gravity",
            "layout_alignParentLeft", "layout_alignParentStart",
            "layout_alignParentRight", "layout_alignParentEnd",
            "layout_toLeftOf", "layout_toStartOf",
            "layout_toRightOf", "layout_toEndOf",
            "layout_alignLeft", "layout_alignStart",
            "layout_alignRight", "layout_alignEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "layout_marginLeft", "layout_marginStart",
            "layout_marginRight", "layout_marginEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        int targetSdk = context.getProject().getTargetSdk();

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            if (minSdk < 17) {
                Element element = attribute.getOwnerElement();
                boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
                boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);
                if (!hasGravity && !hasLayoutGravity) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify `gravity` or `layout_gravity` when using `textAlignment`"
                    );
                }
            }
            return;
        }

        if (SdkConstants.ATTR_GRAVITY.equals(name) || SdkConstants.ATTR_LAYOUT_GRAVITY.equals(name)) {
            checkGravity(context, attribute, minSdk, targetSdk);
            return;
        }

        if (isRtlAttributeName(name)) {
            if (minSdk < 17) {
                String ltrName = convertNewToOld(name);
                Element element = attribute.getOwnerElement();
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, ltrName)) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        String.format("To support older versions than API 17, you must also specify `android:%s` when using `android:%s`", ltrName, name)
                    );
                } else {
                    String rtlValue = attribute.getValue();
                    String ltrValue = element.getAttributeNS(SdkConstants.ANDROID_URI, ltrName);
                    if (rtlValue != null && ltrValue != null && !normalizeValue(rtlValue).equals(normalizeValue(ltrValue))) {
                        context.report(
                            COMPAT,
                            attribute,
                            context.getLocation(attribute),
                            String.format("Inconsistent alignment specification between `android:%s` and `android:%s`: `%s` vs `%s`", name, ltrName, rtlValue, ltrValue)
                        );
                    }
                }
            }
        } else if (isLtrAttributeName(name)) {
            if (minSdk < 17 && targetSdk >= 17) {
                String rtlName = convertOldToNew(name);
                Element element = attribute.getOwnerElement();
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, rtlName)) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        String.format("To support RTL, you must also specify `android:%s` when using `android:%s`", rtlName, name)
                    );
                }
            }
        }
    }

    private void checkGravity(XmlContext context, Attr attribute, int minSdk, int targetSdk) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        String[] parts = value.split("\\|");
        boolean hasLeft = false;
        boolean hasRight = false;
        boolean hasStart = false;
        boolean hasEnd = false;

        for (String part : parts) {
            String gravity = part.trim();
            if ("left".equals(gravity)) {
                hasLeft = true;
            } else if ("right".equals(gravity)) {
                hasRight = true;
            } else if ("start".equals(gravity)) {
                hasStart = true;
            } else if ("end".equals(gravity)) {
                hasEnd = true;
            }
        }

        if (minSdk < 17) {
            if (hasStart && !hasLeft) {
                context.report(
                    COMPAT,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify `left` when using `start`"
                );
            }
            if (hasEnd && !hasRight) {
                context.report(
                    COMPAT,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify `right` when using `end`"
                );
            }
            if (targetSdk >= 17) {
                if (hasLeft && !hasStart) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support RTL, you must also specify `start` when using `left`"
                    );
                }
                if (hasRight && !hasEnd) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support RTL, you must also specify `end` when using `right`"
                    );
                }
            }
        }
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("@+id/")) {
            return "@id/" + value.substring(5);
        }
        return value;
    }
}