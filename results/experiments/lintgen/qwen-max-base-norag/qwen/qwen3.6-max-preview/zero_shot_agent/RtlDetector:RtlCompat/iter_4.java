package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
            "if you are supporting older versions than API 17, you must **also** specify a " +
            "gravity or layout_gravity attribute, since older platforms will ignore the " +
            "`textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String[] ATTRIBUTES = {
            "alignParentLeft", "alignParentStart",
            "alignParentRight", "alignParentEnd",
            "toLeftOf", "toStartOf",
            "toRightOf", "toEndOf",
            "alignLeft", "alignStart",
            "alignRight", "alignEnd",
            "marginLeft", "marginStart",
            "marginRight", "marginEnd",
            "paddingLeft", "paddingStart",
            "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart",
            "drawableRight", "drawableEnd",
            "gravity", "textAlignment"
    };

    public static String convertNewToOld(String newName) {
        String prefix = "";
        String base = newName;
        if (newName.startsWith("layout_")) {
            prefix = "layout_";
            base = newName.substring(7);
        }
        for (int i = 1; i < ATTRIBUTES.length; i += 2) {
            if (ATTRIBUTES[i].equals(base)) {
                return prefix + ATTRIBUTES[i - 1];
            }
        }
        return newName;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        String oldName = convertNewToOld(name);
        if (oldName.equals(name)) {
            return;
        }

        int minSdk = 1;
        if (context.getMainProject().getMinSdkVersion() != null) {
            minSdk = context.getMainProject().getMinSdkVersion().getApiLevel();
        }
        if (minSdk >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasFallback = false;
        String fallbackMessage;

        if (name.equals(SdkConstants.ATTR_TEXT_ALIGNMENT)) {
            boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
            boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);
            hasFallback = hasGravity || hasLayoutGravity;
            fallbackMessage = "`android:gravity` or `android:layout_gravity`";
        } else {
            hasFallback = element.hasAttributeNS(SdkConstants.ANDROID_URI, oldName);
            fallbackMessage = "`android:" + oldName + "`";
        }

        if (!hasFallback) {
            String message = "To support older versions than API 17 (project specifies " + minSdk +
                    ") you should **also** specify " + fallbackMessage;
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}