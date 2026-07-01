package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(...);
    @Override public boolean appliesTo(ResourceFolderType folderType) { return folderType == ResourceFolderType.LAYOUT; }
    @Override public Collection<String> getApplicableElements() { return ALL; }
    @Override public void visitElement(XmlContext context, Element element) { checkElement(context, element); }
    private static void checkElement(XmlContext context, Element element) { ... }
    private static boolean isView(Element element) { ... }
    public static boolean hasLayoutVariations(File file) { ... }
}