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
import java.util.HashMap;
import java.util.Map;

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

    private static final Map<String, String> COMPAT_MAP = new HashMap<>();
    static {
        COMPAT_MAP.put(SdkConstants.ATTR_TEXT_ALIGNMENT, null);
        COMPAT_MAP.put("textDirection", null);
        COMPAT_MAP.put("layoutDirection", null);
        COMPAT_MAP.put("layout_alignParentStart", "layout_alignParentLeft");
        COMPAT_MAP.put("layout_alignParentEnd", "layout_alignParentRight");
        COMPAT_MAP.put("layout_toStartOf", "layout_toLeftOf");
        COMPAT_MAP.put("layout_toEndOf", "layout_toRightOf");
        COMPAT_MAP.put("layout_alignStart", "layout_alignLeft");
        COMPAT_MAP.put("layout_alignEnd", "layout_alignRight");
        COMPAT_MAP.put("layout_marginStart", "layout_marginLeft");
        COMPAT_MAP.put("layout_marginEnd", "layout_marginRight");
        COMPAT_MAP.put(SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_LEFT);
        COMPAT_MAP.put(SdkConstants.ATTR_PADDING_END, SdkConstants.ATTR_PADDING_RIGHT);
        COMPAT_MAP.put("drawableStart", "drawableLeft");
        COMPAT_MAP.put("drawableEnd", "drawableRight");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return COMPAT_MAP.keySet();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        int minSdk = context.getMainProject().getMinSdkVersion().getApiLevel();
        if (minSdk >= 17) {
            return;
        }

        String name = attribute.getLocalName();
        String fallback = COMPAT_MAP.get(name);
        Element element = attribute.getOwnerElement();
        String message;

        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            boolean hasGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
            boolean hasLayoutGravity = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);
            if (hasGravity || hasLayoutGravity) {
                return;
            }
            message = "To support older versions than API 17 (project specifies " + minSdk +
                    ") you should **also** specify `android:gravity` or `android:layout_gravity`";
        } else if (fallback != null) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, fallback)) {
                return;
            }
            String value = attribute.getValue();
            message = "To support older versions than API 17 (project specifies " + minSdk +
                    ") you should **also** specify `android:" + fallback + "=\"" + value + "\"`";
        } else {
            message = "To support older versions than API 17 (project specifies " + minSdk +
                    ") you should **also** specify corresponding left/right or gravity attributes";
        }

        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}