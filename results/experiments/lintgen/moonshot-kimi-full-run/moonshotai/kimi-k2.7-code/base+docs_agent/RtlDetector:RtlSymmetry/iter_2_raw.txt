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
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    private static final int RTL_API = 17;

    public static final String[] ATTRIBUTES =
            new String[] {
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
                SdkConstants.ATTR_PADDING_LEFT,
                SdkConstants.ATTR_PADDING_START,
                SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_PADDING_END,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                SdkConstants.ATTR_LAYOUT_MARGIN_START,
                SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN_END,
                SdkConstants.ATTR_DRAWABLE_LEFT,
                SdkConstants.ATTR_DRAWABLE_START,
                SdkConstants.ATTR_DRAWABLE_RIGHT,
                SdkConstants.ATTR_DRAWABLE_END,
            };

    public static final Issue RTL_ENABLED =
            Issue.create(
                    "RtlEnabled",
                    "RTL not enabled",
                    "To support right-to-left layouts, set `android:supportsRtl=\"true\"` in the "
                            + "`<application>` element of your AndroidManifest.xml.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.MANIFEST_SCOPE));

    public static final Issue RTL_SYMMETRY =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final Issue RTL_HARDCODED =
            Issue.create(
                    "RtlHardcoded",
                    "Using left/right instead of start/end",
                    "Using left/right instead of start/end attributes is a common source of bugs in "
                            + "right-to-left locales. Consider using start/end attributes instead.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, String> sOldToNew = new HashMap<>();
    private static final Map<String, String> sNewToOld = new HashMap<>();

    static {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            String oldName = ATTRIBUTES[i];
            String newName = ATTRIBUTES[i + 1];
            sOldToNew.put(oldName, newName);
            sNewToOld.put(newName, oldName);
        }
    }

    public static String convertOldToNew(String name) {
        return sOldToNew.get(name);
    }

    public static String convertNewToOld(String name) {
        return sNewToOld.get(name);
    }

    public static boolean isRtlAttributeName(String name) {
        return sNewToOld.containsKey(name);
    }

    public static String convertToOppositeDirection(String name) {
        if (name.contains("Left")) {
            return name.replace("Left", "Right");
        } else if (name.contains("Right")) {
            return name.replace("Right", "Left");
        } else if (name.contains("Start")) {
            return name.replace("Start", "End");
        } else if (name.contains("End")) {
            return name.replace("End", "Start");
        }
        return null;
    }

    public static int getFolderVersion(File file) {
        if (file == null) {
            return -1;
        }
        String name = file.getName();
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
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            checkSymmetry(context, attribute, name);
            checkHardcoded(context, attribute, name);
        }
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (SdkConstants.TAG_APPLICATION.equals(element.getLocalName())) {
            checkRtlEnabled(context, element);
        }
    }

    private static void checkRtlEnabled(XmlContext context, Element element) {
        int targetSdk = context.getProject().getTargetSdk();
        int minSdk = context.getProject().getMinSdk();
        if (targetSdk < RTL_API && minSdk < RTL_API) {
            return;
        }

        Attr supportsRtl =
                element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "supportsRtl");
        if (supportsRtl != null && SdkConstants.VALUE_TRUE.equals(supportsRtl.getValue())) {
            return;
        }

        context.report(
                RTL_ENABLED,
                element,
                context.getLocation(element),
                "RTL not enabled");
    }

    private static void checkSymmetry(XmlContext context, Attr attribute, String name) {
        if (SdkConstants.ATTR_PADDING_LEFT.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_PADDING_RIGHT)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING_HORIZONTAL)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_PADDING_RIGHT);
            }
        } else if (SdkConstants.ATTR_PADDING_RIGHT.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_PADDING_LEFT)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING_HORIZONTAL)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_PADDING_LEFT);
            }
        } else if (SdkConstants.ATTR_PADDING_START.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_PADDING_END)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_PADDING_END);
            }
        } else if (SdkConstants.ATTR_PADDING_END.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_PADDING_START)
                    && !hasAttribute(attribute, SdkConstants.ATTR_PADDING)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_PADDING_START);
            }
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
            }
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
            }
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_START.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_END)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_LAYOUT_MARGIN_END);
            }
        } else if (SdkConstants.ATTR_LAYOUT_MARGIN_END.equals(name)) {
            if (!hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN_START)
                    && !hasAttribute(attribute, SdkConstants.ATTR_LAYOUT_MARGIN)) {
                reportSymmetry(context, attribute, SdkConstants.ATTR_LAYOUT_MARGIN_START);
            }
        }
    }

    private static void checkHardcoded(XmlContext context, Attr attribute, String name) {
        int minSdk = context.getProject().getMinSdk();
        if (minSdk < RTL_API) {
            File parent = context.file.getParentFile();
            if (parent == null || getFolderVersion(parent) < RTL_API) {
                return;
            }
        }

        String newName = convertOldToNew(name);
        if (newName != null && !hasAttribute(attribute, newName)) {
            String message = String.format("Consider replacing %1$s with %2$s", name, newName);
            context.report(RTL_HARDCODED, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean hasAttribute(@NotNull Attr attribute, @NotNull String localName) {
        return attribute.getOwnerElement().hasAttributeNS(SdkConstants.ANDROID_URI, localName);
    }

    private static void reportSymmetry(
            @NotNull XmlContext context, @NotNull Attr attribute, @NotNull String counterpart) {
        String message =
                String.format(
                        "When you define %1$s you should probably also define %2$s for "
                                + "right-to-left layout symmetry",
                        attribute.getLocalName(),
                        counterpart);
        context.report(RTL_SYMMETRY, attribute, context.getLocation(attribute), message);
    }
}