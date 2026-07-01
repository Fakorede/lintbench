package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /**
     * Map from likely typo tag names to the correct tag name they should be.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common typos for manifest elements
        TYPOS.put("activty", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("actviity", "activity");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activity-alis", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("sevice", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("servcie", "service");
        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("recever", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("provdier", "provider");
        TYPOS.put("proivder", "provider");
        TYPOS.put("aplplication", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaion", "application");
        TYPOS.put("appliation", "application");
        TYPOS.put("aplication", "application");
        TYPOS.put("appication", "application");
        TYPOS.put("mainfest", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("manifst", "manifest");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("manfiest", "manifest");
        TYPOS.put("manfest", "manifest");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("permisison", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("permssion", "permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("user-permission", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-pemission", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");
        TYPOS.put("uses-sdk", "uses-sdk"); // correct, but check for common typos
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("user-sdk", "uses-sdk");
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-fetaure", "uses-feature");
        TYPOS.put("uses-freature", "uses-feature");
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("user-feature", "uses-feature");
        TYPOS.put("uses-libraray", "uses-library");
        TYPOS.put("uses-libray", "uses-library");
        TYPOS.put("uses-libarary", "uses-library");
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("user-library", "uses-library");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");
        TYPOS.put("intnet-filter", "intent-filter");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("acton", "action");
        TYPOS.put("actoin", "action");
        TYPOS.put("catagory", "category");
        TYPOS.put("caegory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("catgory", "category");
        TYPOS.put("categorie", "category");
        TYPOS.put("dat", "data");
        TYPOS.put("daata", "data");
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("meata-data", "meta-data");
        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-uri-permisson", "grant-uri-permission");
        TYPOS.put("path-permision", "path-permission");
        TYPOS.put("path-permisson", "path-permission");
        TYPOS.put("instrumetation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumntation", "instrumentation");
        TYPOS.put("instumentation", "instrumentation");
        TYPOS.put("suppots-screens", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");
        TYPOS.put("compatiable-screens", "compatible-screens");
        TYPOS.put("compatible-screen", "compatible-screens");
        TYPOS.put("compatbile-screens", "compatible-screens");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TYPOS.keySet().toArray(new String[0]));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String correctName = TYPOS.get(tagName);
        if (correctName != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tagName,
                            correctName));
        }
    }
}