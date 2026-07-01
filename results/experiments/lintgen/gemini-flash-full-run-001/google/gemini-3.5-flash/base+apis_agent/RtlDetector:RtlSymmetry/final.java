package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    public static final Issue USE_START = Issue.create(
            "RtlHardcoded",
            "Using 'left'/'right' instead of 'start'/'end' attributes",
            "To support right-to-left layouts, when targeting minSdkVersion 17 or higher, " +
            "you should use `start` and `end` instead of `left` and `right`.",
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
            "API 17 and higher supports RTL, but if your minSdkVersion is less than 17, " +
            "you should also keep the left/right attributes.",
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
            "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in the manifest.",
            Category.RTL,
            3,
            Severity.WARNING,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public static final String[] ATTRIBUTES = new String[] {
        SdkConstants.ATTR_PADDING_LEFT,                  SdkConstants.ATTR_PADDING_START,
        SdkConstants.ATTR_PADDING_RIGHT,                 SdkConstants.ATTR_PADDING_END,
        SdkConstants.ATTR_DRAWABLE_LEFT,                 SdkConstants.ATTR_DRAWABLE_START,
        SdkConstants.ATTR_DRAWABLE_RIGHT,                SdkConstants.ATTR_DRAWABLE_END,

        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,            SdkConstants.ATTR_LAYOUT_MARGIN_START,
        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,           SdkConstants.ATTR_LAYOUT_MARGIN_END,
        SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,             SdkConstants.ATTR_LAYOUT_TO_START_OF,
        SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,            SdkConstants.ATTR_LAYOUT_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,             SdkConstants.ATTR_LAYOUT_ALIGN_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,            SdkConstants.ATTR_LAYOUT_ALIGN_END,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,      SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,     SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END
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
        checkSymmetry(context, element);
    }

    private void checkSymmetry(XmlContext context, Element element) {
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        checkSymmetry(context, element, SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END);
    }

    private void checkSymmetry(XmlContext context, Element element, String left, String right) {
        boolean hasLeft = element.hasAttributeNS(SdkConstants.ANDROID_URI, left);
        boolean hasRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, right);
        if (hasLeft != hasRight) {
            String defined = hasLeft ? left : right;
            String missing = hasLeft ? right : left;
            Attr attribute = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, defined);
            if (attribute != null) {
                context.report(SYMMETRY, attribute, context.getLocation(attribute),
                        String.format("To support right-to-left layouts, when you define `%1$s` you should also define `%2$s`", defined, missing));
            }
        }
    }

    public static String convertOldToNew(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String old = ATTRIBUTES[i];
            String newAttr = ATTRIBUTES[i + 1];
            if (old.equals(attribute)) {
                return newAttr;
            }
            if (old.startsWith("layout_") && old.substring(7).equals(attribute)) {
                return newAttr.startsWith("layout_") ? newAttr.substring(7) : newAttr;
            }
        }
        if (attribute.endsWith("Left")) {
            return attribute.substring(0, attribute.length() - 4) + "Start";
        } else if (attribute.endsWith("Right")) {
            return attribute.substring(0, attribute.length() - 5) + "End";
        } else if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            String old = ATTRIBUTES[i];
            String newAttr = ATTRIBUTES[i + 1];
            if (newAttr.equals(attribute)) {
                return old;
            }
            if (newAttr.startsWith("layout_") && newAttr.substring(7).equals(attribute)) {
                return old.startsWith("layout_") ? old.substring(7) : old;
            }
        }
        if (attribute.endsWith("Start")) {
            return attribute.substring(0, attribute.length() - 5) + "Left";
        } else if (attribute.endsWith("End")) {
            return attribute.substring(0, attribute.length() - 3) + "Right";
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
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

    public static boolean isRtlAttributeName(String attribute) {
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            String newAttr = ATTRIBUTES[i];
            if (newAttr.equals(attribute)) {
                return true;
            }
            if (newAttr.startsWith("layout_") && newAttr.substring(7).equals(attribute)) {
                return true;
            }
        }
        return attribute.endsWith("Start") || attribute.endsWith("End") || attribute.contains("Start") || attribute.contains("End");
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int version = getFolderVersion(name);
        if (version != -1) {
            return version;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return getFolderVersion(parent.getName());
        }
        return -1;
    }

    private static int getFolderVersion(String name) {
        int index = name.lastIndexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1;
    }
}