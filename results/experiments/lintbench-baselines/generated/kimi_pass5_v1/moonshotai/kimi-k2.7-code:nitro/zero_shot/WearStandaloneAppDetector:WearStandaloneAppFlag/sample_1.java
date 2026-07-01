package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid `<meta-data android:name=\"com.google.android.wearable.standalone\" "
                    + "android:value=\"true\"/>` (or `false`) element to the `<application>` element.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String WEARABLE_HARDWARE_TYPE_WATCH = "android.hardware.type.watch";
    private static final String STANDALONE_META_DATA_NAME = "com.google.android.wearable.standalone";

    private boolean mIsWearable;
    private Element mApplicationElement;

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_USES_FEATURE);
    }

    @Override
    @NonNull
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.MANIFEST_SCOPE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsWearable = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = getAttrValue(element, ATTR_NAME);
            if (WEARABLE_HARDWARE_TYPE_WATCH.equals(name)) {
                String required = getAttrValue(element, ATTR_REQUIRED);
                if (required == null || Boolean.parseBoolean(required)) {
                    mIsWearable = true;
                }
            }
        } else if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsWearable && mApplicationElement != null) {
            checkStandaloneFlag((XmlContext) context, mApplicationElement);
        }
    }

    private void checkStandaloneFlag(@NonNull XmlContext context, @NonNull Element application) {
        boolean found = false;
        boolean valid = false;
        Element invalidElement = null;

        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_META_DATA.equals(element.getTagName())) {
                continue;
            }
            String name = getAttrValue(element, ATTR_NAME);
            if (STANDALONE_META_DATA_NAME.equals(name)) {
                found = true;
                String value = getAttrValue(element, ATTR_VALUE);
                if (isValidBoolean(value)) {
                    valid = true;
                } else {
                    invalidElement = element;
                }
                break;
            }
        }

        if (!found) {
            Location location = context.getLocation(application);
            context.report(ISSUE, location,
                    "Wear apps must declare the `com.google.android.wearable.standalone` meta-data flag in the `<application>` element");
        } else if (!valid && invalidElement != null) {
            Location location = context.getValueLocation(invalidElement, ATTR_VALUE);
            context.report(ISSUE, location,
                    "The `com.google.android.wearable.standalone` meta-data value must be `true` or `false`");
        }
    }

    private static boolean isValidBoolean(@NonNull String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    @NonNull
    private static String getAttrValue(@NonNull Element element, @NonNull String attrName) {
        String value = element.getAttributeNS(ANDROID_URI, attrName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = element.getAttribute(attrName);
        return value != null ? value : "";
    }
}