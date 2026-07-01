package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DRAWABLE_END;
import static com.android.SdkConstants.ATTR_DRAWABLE_LEFT;
import static com.android.SdkConstants.ATTR_DRAWABLE_RIGHT;
import static com.android.SdkConstants.ATTR_DRAWABLE_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_END;
import static com.android.SdkConstants.ATTR_PADDING_HORIZONTAL;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_START;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.SdkConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends ResourceXmlDetector {

    private static final String RTL_PROPERTY = "supportsRtl";

    public static final Issue ISSUE = Issue.create(
            "RtlHardcoded",
            "Using left/right instead of start/end",
            "Using left/right instead of start/end is not advised when targeting API 17+ "
                    + "because it may not behave correctly for right-to-left layouts. "
                    + "Consider using start/end attributes instead.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue ISSUE_ENABLED = Issue.create(
            "RtlEnabled",
            "Right-to-left text not supported",
            "To support right-to-left layouts, set android:supportsRtl=\"true\" in the "
                    + "<application> tag in the manifest when targeting API 17 or higher.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.MANIFEST_SCOPE));

    public static final Issue ISSUE_SYMMETRY = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String[] ATTRIBUTES =
            new String[] {
                ATTR_LAYOUT_ALIGN_PARENT_LEFT, ATTR_LAYOUT_ALIGN_PARENT_START,
                ATTR_LAYOUT_ALIGN_PARENT_RIGHT, ATTR_LAYOUT_ALIGN_PARENT_END,
                ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_ALIGN_START,
                ATTR_LAYOUT_ALIGN_RIGHT, ATTR_LAYOUT_ALIGN_END,
                ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_TO_START_OF,
                ATTR_LAYOUT_TO_RIGHT_OF, ATTR_LAYOUT_TO_END_OF,
                ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_END,
                ATTR_PADDING_LEFT, ATTR_PADDING_START,
                ATTR_PADDING_RIGHT, ATTR_PADDING_END,
                ATTR_DRAWABLE_LEFT, ATTR_DRAWABLE_START,
                ATTR_DRAWABLE_RIGHT, ATTR_DRAWABLE_END,
            };

    private int mTargetSdk = -1;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mTargetSdk = context.getProject().getTargetSdk();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_USES_SDK);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_SDK.equals(tag)) {
            String target = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
            if (!target.isEmpty()) {
                try {
                    mTargetSdk = Integer.parseInt(target);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        } else if (TAG_APPLICATION.equals(tag)) {
            int targetSdk = mTargetSdk != -1 ? mTargetSdk : context.getProject().getTargetSdk();
            if (targetSdk >= 17) {
                String supportsRtl = element.getAttributeNS(ANDROID_URI, RTL_PROPERTY);
                if (!VALUE_TRUE.equals(supportsRtl)) {
                    context.report(
                            ISSUE_ENABLED,
                            element,
                            context.getLocation(element),
                            "Consider adding android:supportsRtl=\"true\" to the <application> tag here when targeting API 17 or higher.");
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();

        checkSymmetry(context, attribute, name);
        checkHardcoded(context, attribute, name);
    }

    private static void checkSymmetry(XmlContext context, Attr attribute, String name) {
        String counterpart;
        String all;
        String horizontal;

        if (ATTR_PADDING_LEFT.equals(name)) {
            counterpart = ATTR_PADDING_RIGHT;
            all = ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            counterpart = ATTR_PADDING_LEFT;
            all = ATTR_PADDING;
            horizontal = ATTR_PADDING_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_RIGHT;
            all = ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else if (ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            counterpart = ATTR_LAYOUT_MARGIN_LEFT;
            all = ATTR_LAYOUT_MARGIN;
            horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
        } else {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.hasAttributeNS(ANDROID_URI, counterpart)
                && !element.hasAttributeNS(ANDROID_URI, all)
                && !element.hasAttributeNS(ANDROID_URI, horizontal)) {
            String message =
                    String.format(
                            "When you define %1$s you should probably also define %2$s for right-to-left symmetry",
                            name, counterpart);
            context.report(ISSUE_SYMMETRY, attribute, context.getLocation(attribute), message);
        }
    }

    private static void checkHardcoded(XmlContext context, Attr attribute, String name) {
        String newName = convertOldToNew(name);
        if (newName.equals(name)) {
            return;
        }

        int folderVersion = getFolderVersion(context.file.getParentFile());
        int targetSdk = context.getProject().getTargetSdk();
        if (folderVersion < 17 && targetSdk < 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.hasAttributeNS(ANDROID_URI, newName)) {
            String message =
                    String.format(
                            "Consider adding %1$s to support right-to-left layouts",
                            ANDROID_NS_NAME + ':' + newName);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    public static boolean isRtlAttributeName(String name) {
        return name.endsWith("Start") || name.endsWith("End");
    }

    public static String convertOldToNew(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Start";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "End";
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Right";
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        if (name.endsWith("Left")) {
            return name.substring(0, name.length() - 4) + "Right";
        } else if (name.endsWith("Right")) {
            return name.substring(0, name.length() - 5) + "Left";
        } else if (name.endsWith("Start")) {
            return name.substring(0, name.length() - 5) + "End";
        } else if (name.endsWith("End")) {
            return name.substring(0, name.length() - 3) + "Start";
        }
        return name;
    }

    public static int getFolderVersion(File dir) {
        String name = dir.getName();
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