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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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
            Scope.RESOURCE_FILE_SCOPE
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

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == null
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        Collection<String> attributes = new ArrayList<>(Arrays.asList(ATTRIBUTES));
        attributes.add(SdkConstants.ATTR_TEXT_ALIGNMENT);
        return attributes;
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

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            checkTextAlignment(context, attribute);
        } else if (isRtlAttributeName(name)) {
            checkRtlCompat(context, attribute);
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

    private static int getApplicableMinSdk(XmlContext context) {
        int minSdk = context.getMainProject().getMinSdk();
        int folderVersion = getFolderVersion(context.file);
        return Math.max(minSdk, folderVersion);
    }

    public static int getFolderVersion(File file) {
        File folder = file.isDirectory() ? file : file.getParentFile();
        if (folder == null) {
            return 0;
        }
        String name = folder.getName();
        int index = name.lastIndexOf("-v");
        if (index != -1 && index + 2 < name.length()) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean hasAndroidAttribute(Element element, String name) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, name);
    }

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
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
}