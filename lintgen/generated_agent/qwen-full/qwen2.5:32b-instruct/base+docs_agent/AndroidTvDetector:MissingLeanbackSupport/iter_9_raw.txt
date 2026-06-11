package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ScreenSize;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;

public class AndroidTvDetector extends Detector implements ManifestScanner {

    private static final String USES_FEATURE = "uses-feature";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String LEANBACK_NAME = "android.software.leanback";

    @Nullable
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of("manifest");
    }

    @NonNull
    @Override
    public Issue getIssue() {
        return ISSUE_MISSING_LEANBACK_SUPPORT;
    }

    private static final Issue ISSUE_MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "The manifest should declare the use of the Leanback user interface required by Android TV.",
            "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Nullable
    @Override
    public List<Issue> check(@NonNull XmlContext context) {
        Element manifestRoot = context.getManifest().getDocument();
        NodeList usesFeatures = manifestRoot.getElementsByTagName(USES_FEATURE);
        boolean leanbackFeatureFound = false;

        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Node node = usesFeatures.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element featureElement = (Element) node;
                String nameAttrValue = featureElement.getAttribute(NAME_ATTRIBUTE);
                if (LEANBACK_NAME.equals(nameAttrValue)) {
                    leanbackFeatureFound = true;
                    break;
                }
            }
        }

        if (!leanbackFeatureFound) {
            return ImmutableList.of(ISSUE_MISSING_LEANBACK_SUPPORT);
        }

        return null;
    }
}