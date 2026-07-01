package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RtlDetector extends Detector implements XmlScanner {

    public static final String[] ATTRIBUTES = new String[] {
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
            SdkConstants.ATTR_DRAWABLE_LEFT,
            SdkConstants.ATTR_DRAWABLE_START,
            SdkConstants.ATTR_DRAWABLE_RIGHT,
            SdkConstants.ATTR_DRAWABLE_END,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
            SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
            SdkConstants.ATTR_LAYOUT_MARGIN_END,
            SdkConstants.ATTR_PADDING_LEFT,
            SdkConstants.ATTR_PADDING_START,
            SdkConstants.ATTR_PADDING_RIGHT,
            SdkConstants.ATTR_PADDING_END,
    };

    private static final Implementation IMPLEMENTATION = new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `textAlignment` attribute is only available on API 17 and higher. "
                    + "However, if you are supporting older versions than API 17, you must also "
                    + "specify a `gravity` or `layout_gravity` attribute with the same value, since "
                    + "older platforms will ignore the `textAlignment` attribute.\n"
                    + "\n"
                    + "Similarly, API 17 adds `start` and `end` alternatives to many `left`/`right` "
                    + "attributes, such as `android:layout_marginStart` and `android:layout_toStartOf`. "
                    + "If you are supporting older versions than API 17, you must also supply the "
                    + "`left`/`right` attribute as well.",
            Category.I18N,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue ENABLED_ISSUE = Issue.create(
            "RtlEnabled",
            "Right-to-left text direction not enabled in manifest",
            "The project references RTL attributes (`...`) but does not explicitly set "
                    + "`android:supportsRtl` to be true in the manifest. When supporting right-to-left "
                    + "layouts, it is recommended to set it to true in the manifest.",
            Category.I18N,
            3,
            Severity.WARNING,
            IMPLEMENTATION
    );

    public static final Issue SYMMETRY_ISSUE = Issue.create(
            "RtlSymmetry",
            "Right-to-left text padding and margin symmetry",
            "When you define `paddingStart` you should also define `paddingEnd` for symmetry, "
                    + "and if you define `paddingLeft` you should define `paddingRight`.",
            Category.I18N,
            2,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private boolean mSupportsRtl;
    private boolean mFoundRtlAttributes;
    private String mFirstRtlAttribute;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == null
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> attributes = new ArrayList<>(Arrays.asList(ATTRIBUTES));
        attributes.add(SdkConstants.ATTR_TEXT_ALIGNMENT);
        attributes.add(SdkConstants.ATTR_SUPPORTS_RTL);
        return attributes;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mSupportsRtl = false;
        mFoundRtlAttributes = false;
        mFirstRtlAttribute = null;
    }

    @Override
    public void afterCheckProject(Context context) {
        if (mFoundRtlAttributes && !mSupportsRtl) {
            File manifest = context.getMainProject().getManifestFile();
            if (manifest != null) {
                String name = mFirstRtlAttribute != null ? mFirstRtlAttribute : "paddingStart";
                String message = String.format(
                        "The project references RTL attributes (`%1$s`) but does not explicitly set "
                                + "`android:supportsRtl` to be true in the manifest. When supporting "
                                + "right-to-left layouts, it is recommended to set it to true in the manifest.",
                        name);
                context.report(ENABLED_ISSUE, manifest, Location.create(manifest), message);
            }
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        if (SdkConstants.ATTR_SUPPORTS_RTL.equals(name)) {
            if (SdkConstants.VALUE_TRUE.equals(attribute.getValue())) {
                mSupportsRtl = true;
            }
            return;
        }

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            mFoundRtlAttributes = true;
            if (mFirstRtlAttribute == null) {
                mFirstRtlAttribute = name;
            }
            checkTextAlignment(context, attribute);
            return;
        }

        if (isRtlAttributeName(name)) {
            mFoundRtlAttributes = true;
            if (mFirstRtlAttribute == null) {
                mFirstRtlAttribute = name;
            }
            checkRtlCompat(context, attribute);
        }

        if (name.contains("padding") || name.contains("margin")) {
            checkSymmetry(context, attribute);
        }
    }

    private void checkTextAlignment(XmlContext context, Attr attribute) {
        if (getApplicableMinSdk(context) >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, SdkConstants.ATTR_GRAVITY)
                || hasAndroidAttribute(element, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `textAlignment` attribute is only available on API 17 and higher. "
                        + "When supporting older versions, you should also specify a `gravity` or "
                        + "`layout_gravity` attribute with the same value."
        );
    }

    private void checkRtlCompat(XmlContext context, Attr attribute) {
        if (getApplicableMinSdk(context) >= 17) {
            return;
        }

        String name = attribute.getLocalName();
        String oldName = convertNewToOld(name);
        if (oldName.equals(name)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, oldName)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                String.format(
                        "The `%1$s` attribute is only available on API 17 and higher. "
                                + "When supporting older versions, you should also add a `%2$s` attribute "
                                + "with the same value.",
                        name,
                        oldName
                )
        );
    }

    private void checkSymmetry(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        String opposite = convertToOppositeDirection(name);
        if (opposite.equals(name)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAndroidAttribute(element, opposite)) {
            return;
        }

        context.report(
                SYMMETRY_ISSUE,
                attribute,
                context.getValueLocation(attribute),
                String.format(
                        "If you are using attribute `%1$s` for RTL compatibility, you should also define `%2$s`.",
                        name,
                        opposite
                )
        );
    }

    private static int getApplicableMinSdk(XmlContext context) {
        int minSdk = context.getMainProject().getMinSdk();
        File parent = context.getFile().getParentFile();
        if (parent != null) {
            int folderVersion = getFolderVersion(parent);
            if (folderVersion > minSdk) {
                minSdk = folderVersion;
            }
        }
        return minSdk;
    }

    public static int getFolderVersion(File file) {
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
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static String convertOldToNew(String name) {
        int index = name.indexOf("Left");
        if (index != -1) {
            return name.substring(0, index) + "Start" + name.substring(index + 4);
        }
        index = name.indexOf("Right");
        if (index != -1) {
            return name.substring(0, index) + "End" + name.substring(index + 5);
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        int index = name.indexOf("Start");
        if (index != -1) {
            return name.substring(0, index) + "Left" + name.substring(index + 5);
        }
        index = name.indexOf("End");
        if (index != -1) {
            return name.substring(0, index) + "Right" + name.substring(index + 3);
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        int index = name.indexOf("Left");
        if (index != -1) {
            return name.substring(0, index) + "Right" + name.substring(index + 4);
        }
        index = name.indexOf("Right");
        if (index != -1) {
            return name.substring(0, index) + "Left" + name.substring(index + 5);
        }
        index = name.indexOf("Start");
        if (index != -1) {
            return name.substring(0, index) + "End" + name.substring(index + 5);
        }
        index = name.indexOf("End");
        if (index != -1) {
            return name.substring(0, index) + "Start" + name.substring(index + 3);
        }
        return name;
    }

    private static boolean hasAndroidAttribute(Element element, String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }
}