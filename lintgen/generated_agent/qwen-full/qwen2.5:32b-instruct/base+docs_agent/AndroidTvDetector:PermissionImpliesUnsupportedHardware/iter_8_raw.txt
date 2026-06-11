package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedHardwarePermission",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature.",
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final List<String> PERMISSION_IMPLYING_HARDWARE = Collections.unmodifiableList(Arrays.asList(
            SdkConstants.PERMISSION_CAMERA,
            SdkConstants.PERMISSION_RECORD_AUDIO
    ));

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.ELEMENT_PERMISSION);
    }

    @Nullable
    @Override
    public List<String> getApplicableAttributes(@NonNull Element element) {
        if (SdkConstants.ELEMENT_PERMISSION.equals(element.getTagName())) {
            return Collections.singletonList(SdkConstants.ATTR_NAME);
        }
        return null;
    }

    @Override
    protected void checkResourceFile(@NonNull Context context, @NonNull Element resourceRoot) {
        for (Element element : context.getResources().getElements(resourceRoot)) {
            Attr nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME);
            if (nameAttr != null && SdkConstants.ELEMENT_PERMISSION.equals(element.getTagName())) {
                String permissionName = nameAttr.getValue();
                for (String impliedPermission : PERMISSION_IMPLYING_HARDWARE) {
                    if (permissionName.equals(impliedPermission)) {
                        Location location = context.getLocation(element);
                        context.report(ISSUE, element, location,
                                "Permission `" + permissionName + "` implies hardware feature. Consider declaring the corresponding `uses-feature` with `required=\"false\"`.");
                    }
                }
            }
        }
    }

}