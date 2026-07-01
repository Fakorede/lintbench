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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

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
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue ISSUE = COMPAT;

    public static final Issue USE_START_END = Issue.create(
        "RtlHardcoded",
        "Using 'left'/'right' instead of 'start'/'end' attributes",
        "To support right-to-left layouts, use start/end instead of left/right.",
        Category.RTL, 5, Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "To use RTL attributes, you must enable RTL support in your manifest.",
        Category.RTL, 3, Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue SYMMETRY = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "If you specify padding/margin on one side, you should specify it on the other.",
        Category.RTL, 3, Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String[] ATTRIBUTES = new String[] {
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd"
    };

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            "textAlignment",
            "gravity",
            "layout_gravity",
            "paddingStart",
            "paddingEnd",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_alignParentStart",
            "layout_alignParentEnd",
            "layout_toStartOf",
            "layout_toEndOf",
            "layout_alignStart",
            "layout_alignEnd",
            "drawableStart",
            "drawableEnd",
            "drawableRelativeStart",
            "drawableRelativeEnd"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 17) {
            return;
        }
        if (context.getFolderVersion() >= 17) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            if (name.contains(":")) {
                name = name.substring(name.indexOf(':') + 1);
            }
        }

        if ("textAlignment".equals(name)) {
            Element element = attribute.getOwnerElement();
            boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "gravity");
            boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
            if (!hasGravity && !hasLayoutGravity) {
                context.report(
                    COMPAT,
                    attribute,
                    context.getLocation(attribute),
                    "To support older versions than API 17, you must also specify a gravity or layout_gravity attribute"
                );
            }
        } else if ("gravity".equals(name) || "layout_gravity".equals(name)) {
            String value = attribute.getValue();
            if (value != null) {
                boolean hasStart = false;
                boolean_hasEnd = false;
                boolean hasLeft = false;
                boolean hasRight = false;
                for (String part : value.split("\\|")) {
                    part = part.trim();
                    if ("start".equals(part)) {
                        hasStart = true;
                    } else if ("end".equals(part)) {
                        hasEnd = true;
                    } else if ("left".equals(part)) {
                        hasLeft = true;
                    } else if ("right".equals(part)) {
                        hasRight = true;
                    }
                }
                if (hasStart && !hasLeft) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify `left` in gravity"
                    );
                }
                if (hasEnd && !hasRight) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        "To support older versions than API 17, you must also specify `right` in gravity"
                    );
                }
            }
        } else {
            String oldAttr = convertNewToOld(name);
            if (oldAttr != null && !oldAttr.equals(name)) {
                Element element = attribute.getOwnerElement();
                boolean hasOld = element.hasAttributeNS(SdkConstants.ANDROID_URI, oldAttr);
                
                if (!hasOld) {
                    if (("paddingStart".equals(name) || "paddingEnd".equals(name)) 
                        && element.hasAttributeNS(SdkConstants.ANDROID_URI, "padding")) {
                        hasOld = true;
                    }
                    if (("layout_marginStart".equals(name) || "layout_marginEnd".equals(name)) 
                        && element.hasAttributeNS(SdkConstants.ANDROID_URI, "layout_margin")) {
                        hasOld = true;
                    }
                }
                
                if (!hasOld) {
                    context.report(
                        COMPAT,
                        attribute,
                        context.getLocation(attribute),
                        String.format("To support older versions than API 17, you must also specify `android:%s`", oldAttr)
                    );
                }
            }
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Start";
        }
        if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        }
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        }
        if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if ("drawableRelativeStart".equals(attribute)) {
            return "drawableLeft";
        }
        if ("drawableRelativeEnd".equals(attribute)) {
            return "drawableRight";
        }
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        }
        if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Right";
        }
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        }
        if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Right";
        }
        if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        }
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        }
        if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Start";
        }
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
}