package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final Set<String> VALID_USES_NAMES =
            new HashSet<>(Arrays.asList("media", "notification", "sms"));

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid name attribute for uses element",
            "The `<uses>` element inside `<automotiveApp>` must have an `android:name` "
                    + "attribute with one of the supported values: `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !TAG_AUTOMOTIVE_APP.equals(parent.getNodeName())) {
            return;
        }

        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (attr == null) {
            Location location = context.getLocation(element);
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    location,
                    "The `<uses>` element must specify an `android:name` attribute");
            return;
        }

        String name = attr.getValue();
        if (name == null || name.isEmpty() || !VALID_USES_NAMES.contains(name)) {
            Location location = context.getLocation(attr);
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    location,
                    "Invalid `android:name` value \"" + name + "\" for `<uses>`; "
                            + "must be one of: media, notification, sms");
        }
    }
}