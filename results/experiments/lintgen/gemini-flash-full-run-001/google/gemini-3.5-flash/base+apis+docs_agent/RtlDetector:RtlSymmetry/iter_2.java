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
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue USE_START_END = Issue.create(
        "RtlHardcoded",
        "Using 'left'/'right' instead of 'start'/'end' attributes",
        "To support right-to-left layouts, when targeting API level 17 or higher, you should " +
        "use the `start` and `end` attributes instead of `left` and `right` for " +
        "horizontal padding, margins and positioning.",
        Category.I18N,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue COMPATIBILITY = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API level 17 introduced RTL support. If your `minSdkVersion` is less than 17, you should " +
        "still use `left`/`right` in addition to `start`/`end` to ensure that the layout " +
        "renders correctly on older platforms.",
        Category.I18N,
        6,
        Severity.ERROR,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in your manifest.",
        Category.I18N,
        3,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue ISSUE = SYMMETRY;

    public static final String[] ATTRIBUTES = new String[] {
        "layout_alignParentLeft",  "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_marginLeft",        "layout_marginStart",
        "layout_marginRight",       "layout_marginEnd",
        "layout_alignLeft",         "layout_alignStart",
        "layout_alignRight",        "layout_alignEnd",
        "layout_toLeftOf",          "layout_toStartOf",
        "layout_toRightOf",         "layout_toEndOf",
        "paddingLeft",              "paddingStart",
        "paddingRight",             "paddingEnd",
        "drawableLeft",             "drawableStart",
        "drawableRight",            "drawableEnd",
        "alignParentLeft",          "alignParentStart",
        "alignParentRight",         "alignParentEnd"
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

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int index = name.lastIndexOf("-v");
        if (index != -1) {
            String versionString = name.substring(index + 2);
            try {
                return Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                // ignore
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
        checkSymmetry(context, element);

        int minSdk = context.getProject().getMinSdk();
        int targetSdk = context.getProject().getTargetSdk();

        int folderVersion = getFolderVersion(context.file.getParentFile());
        if (folderVersion >= 17) {
            minSdk = Math.max(minSdk, folderVersion);
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            String namespace = attr.getNamespaceURI();
            if (!SdkConstants.ANDROID_URI.equals(namespace) || name == null) {
                continue;
            }

            String newName = convertOldToNew(name);
            if (!newName.equals(name)) {
                if (targetSdk >= 17) {
                    boolean hasStartEnd = element.hasAttributeNS(SdkConstants.ANDROID_URI, newName);
                    if (!hasStartEnd) {
                        if (minSdk >= 17) {
                            context.report(USE_START_END, attr, context.getLocation(attr),
                                String.format("Use `%1$s` instead of `%2$s` to ensure correct behavior on right-to-left layouts", newName, name));
                        } else {
                            context.report(COMPATIBILITY, attr, context.getLocation(attr),
                                String.format("To support older versions than API 17 (which can use `%1$s`), you should also define `%2$s`", newName, name));
                        }
                    }
                }
            } else {
                String oldName = convertNewToOld(name);
                if (!oldName.equals(name)) {
                    if (minSdk < 17) {
                        boolean hasLeftRight = element.hasAttributeNS(SdkConstants.ANDROID_URI, oldName);
                        if (!hasLeftRight) {
                            context.report(COMPATIBILITY, attr, context.getLocation(attr),
                                String.format("To support older versions than API 17, you should also define `%1$s` when you define `%2$s`", oldName, name));
                        }
                    }

                    if (targetSdk >= 17 && !context.getProject().getSupportsRtl()) {
                        context.report(ENABLED, attr, context.getLocation(attr),
                            "To use RTL attributes, you must set `android:supportsRtl=\"true\"` in the manifest");
                    }
                }
            }
        }
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
        if (hasLeft && !hasRight) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, left);
            context.report(SYMMETRY, attr, context.getLocation(attr),
                String.format("When you define `%1$s` you should also define `%2$s` for symmetry", left, right));
        } else if (hasRight && !hasLeft) {
            Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, right);
            context.report(SYMMETRY, attr, context.getLocation(attr),
                String.format("When you define `%1$s` you should also define `%2$s` for symmetry", right, left));
        }
    }
}