package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue INVALID_USES_TAG_ATTRIBUTE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid name attribute for uses element",
                    "The <uses> element in <automotiveApp> should contain a valid value for the "
                            + "name attribute. Valid values are media, notification, or sms.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Collection<String> VALID_USES_NAMES =
            Collections.unmodifiableCollection(Arrays.asList("media", "notification", "sms"));

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isAutomotiveAppUsesElement(element)) {
            return;
        }

        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            name = element.getAttributeNS(ANDROID_URI, "name");
        }

        if (name.isEmpty()) {
            return;
        }

        if (!VALID_USES_NAMES.contains(name)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    context.getLocation(element),
                    "Invalid name attribute for <uses>. Valid values are media, notification, or sms.");
        }
    }

    private static boolean isAutomotiveAppUsesElement(@NonNull Element element) {
        if (!(element.getParentNode() instanceof Element)) {
            return false;
        }
        Element parent = (Element) element.getParentNode();
        return "automotiveApp".equals(parent.getTagName());
    }
}