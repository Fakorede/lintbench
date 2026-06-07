SOURCE = '''
// EXAMPLE: ManifestPermissionAttributeDetector (Java, XmlScanner — manifest attribute check)
// Issue: InvalidPermission
// Explanation: Not all elements support the permission attribute. If set on an
// invalid element it is a no-op and ignored.

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_PATH_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class ManifestPermissionAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidPermission",
                    "Invalid Permission Attribute",
                    "Not all elements support the permission attribute. If a permission is set on"
                            + " an invalid element, it is a no-op and ignored. Ensure that this"
                            + " permission attribute was set on the correct element.",
                    Category.SECURITY,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            ManifestPermissionAttributeDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_PERMISSION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String parent = attribute.getOwnerElement().getTagName();
        switch (parent) {
            case NODE_ACTIVITY:
            case NODE_APPLICATION:
            case NODE_PROVIDER:
            case NODE_SERVICE:
            case NODE_RECEIVER:
            case NODE_ACTIVITY_ALIAS:
            case NODE_PATH_PERMISSION:
                return;
        }
        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Protecting an unsupported element with a permission is a no-op and "
                        + "potentially dangerous");
    }
}
'''.strip()
