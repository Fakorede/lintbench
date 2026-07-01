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

import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must also specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = {
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "marginLeft", "marginStart",
            "marginRight", "marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
            "left", "start",
            "right", "end"
    };

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getMainProject().getMinSdk() >= 17) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (!SdkConstants.ANDROID_URI.equals(namespace)) {
            return;
        }

        String name = attribute.getLocalName();
        String value = attribute.getValue();
        Element element = attribute.getOwnerElement();

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "gravity");
            String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
            if (gravity.isEmpty() && layoutGravity.isEmpty()) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `textAlignment`, also specify `android:gravity` or `android:layout_gravity` for compatibility with API < 17");
            }
            return;
        }

        if ("gravity".equals(name) || "layout_gravity".equals(name)) {
            boolean hasStart = value.contains("start");
            boolean hasEnd = value.contains("end");
            boolean hasLeft = value.contains("left");
            boolean hasRight = value.contains("right");

            if ((hasStart && !hasLeft) || (hasEnd && !hasRight)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `start` or `end` in `" + name + "`, also specify `left` or `right` for compatibility with API < 17");
            }
            return;
        }

        String oldAttr = getOldAttribute(name);
        if (oldAttr != null) {
            String oldVal = element.getAttributeNS(SdkConstants.ANDROID_URI, oldAttr);
            if (oldVal.isEmpty()) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "When using `" + name + "`, also specify `android:" + oldAttr + "` for compatibility with API < 17");
            }
        }
    }

    private String getOldAttribute(String name) {
        boolean hasLayoutPrefix = name.startsWith("layout_");
        String baseName = hasLayoutPrefix ? name.substring(7) : name;
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(baseName)) {
                return hasLayoutPrefix ? "layout_" + ATTRIBUTES[i - 1] : ATTRIBUTES[i - 1];
            }
        }
        return null;
    }
}