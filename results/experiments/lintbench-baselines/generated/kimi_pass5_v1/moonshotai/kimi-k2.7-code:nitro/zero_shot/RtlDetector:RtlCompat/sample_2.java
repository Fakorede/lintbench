package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class RtlDetector extends ResourceXmlDetector {

    private static final int TEXT_ALIGNMENT_API = 17;

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The `android:textAlignment` attribute is only available on API 17 and "
                    + "higher. When your application's `minSdkVersion` is lower than 17, "
                    + "you should also specify `android:gravity` or "
                    + "`android:layout_gravity` so that the intended alignment is applied "
                    + "on older devices that ignore `textAlignment`.",
            Category.RTL,
            6,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public @NonNull Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType... folderTypes) {
        return Arrays.asList(folderTypes).contains(ResourceFolderType.LAYOUT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        AndroidVersion minSdk = context.getMainProject().getMinSdkVersion();
        if (minSdk != null && minSdk.getApiLevel() >= TEXT_ALIGNMENT_API) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.getAttributeNS(ANDROID_URI, ATTR_GRAVITY).isEmpty()
                || !element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY).isEmpty()) {
            return;
        }

        String message = "When using `android:textAlignment` with `minSdkVersion` "
                + "below 17, you must also specify `android:gravity` or "
                + "`android:layout_gravity` for compatibility with older devices.";
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}