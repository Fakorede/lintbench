package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends Detector implements XmlScanner {

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
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
            "textAlignment",
            "layout_alignParentLeft", "layout_alignParentStart", "layout_alignParentRight", "layout_alignParentEnd",
            "layout_alignLeft", "layout_alignStart", "layout_alignRight", "layout_alignEnd",
            "layout_marginLeft", "layout_marginStart", "layout_marginRight", "layout_marginEnd",
            "paddingLeft", "paddingStart", "paddingRight", "paddingEnd",
            "drawableLeft", "drawableStart", "drawableRight", "drawableEnd",
            "layout_toLeftOf", "layout_toStartOf", "layout_toRightOf", "layout_toEndOf"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        int minSdk = context.getProject().getMinSdk();
        int targetSdk = context.getProject().getTargetSdk();

        if (name.equals("textAlignment")) {
            if (minSdk < 17) {
                String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "gravity");
                String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, "layout_gravity");
                if (gravity.isEmpty() && layoutGravity.isEmpty()) {
                    String message = String.format(
                        "Attribute `%1$s` is only used in API level 17 and higher " +
                        "(current min is %2$d); you should also specify `android:gravity` or `android:layout_gravity`",
                        name, minSdk);
                    context.report(ISSUE, attribute, context.getLocation(attribute), message);
                }
            }
            return;
        }

        boolean isStartOrEnd = name.endsWith("Start") || name.endsWith("End");
        boolean isLeftOrRight = name.endsWith("Left") || name.endsWith("Right");

        if (isStartOrEnd && minSdk < 17) {
            String counterpart = getCounterpart(name, true);
            if (counterpart != null && !element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
                String message = String.format(
                    "To support older versions than API 17 (project specifies %1$d) you should **also** specify attribute `%2$s`",
                    minSdk, "android:" + counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        } else if (isLeftOrRight && targetSdk >= 17) {
            String counterpart = getCounterpart(name, false);
            if (counterpart != null && !element.hasAttributeNS(SdkConstants.ANDROID_URI, counterpart)) {
                String message = String.format(
                    "To support RTL from API 17 and higher (project targets %1$d) you should **also** specify attribute `%2$s`",
                    targetSdk, "android:" + counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    private static String getCounterpart(String name, boolean toLeftRight) {
        if (toLeftRight) {
            if (name.endsWith("Start")) return name.substring(0, name.length() - 5) + "Left";
            if (name.endsWith("End")) return name.substring(0, name.length() - 3) + "Right";
        } else {
            if (name.endsWith("Left")) return name.substring(0, name.length() - 4) + "Start";
            if (name.endsWith("Right")) return name.substring(0, name.length() - 5) + "End";
        }
        return null;
    }
}