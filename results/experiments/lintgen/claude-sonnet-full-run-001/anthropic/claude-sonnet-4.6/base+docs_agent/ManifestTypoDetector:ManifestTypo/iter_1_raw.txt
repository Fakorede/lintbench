package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    /**
     * Map from misspelling to correct tag name.
     */
    private static final Map<String, String> MISSPELLING_TO_CORRECT;

    static {
        MISSPELLING_TO_CORRECT = new HashMap<>();

        // manifest
        addTypos("manifest",
                "manifets", "manifiest", "mainifest", "mainfest", "manifst", "menifest",
                "manigest", "manifast", "manifeest");

        // application
        addTypos("application",
                "aplcation", "applicaton", "applicaion", "applcation", "aplication",
                "applicaiton", "appliation", "appication", "applicaton");

        // activity
        addTypos("activity",
                "actvity", "activty", "activiy", "activiti", "acivity", "ativity",
                "activitiy");

        // service
        addTypos("service",
                "sevice", "serivce", "servce", "servcie", "srevice", "serice");

        // receiver
        addTypos("receiver",
                "reciver", "reciever", "recever", "recevier", "receiever");

        // provider
        addTypos("provider",
                "provder", "providor", "proivder", "provdier", "providr", "provier");

        // intent-filter
        addTypos("intent-filter",
                "intent-fliter", "intent-filtr", "intent-filer", "intentfilter",
                "intent-filtter", "inten-filter", "intnet-filter");

        // action
        addTypos("action",
                "acton", "actioin", "actoin", "acion", "acction");

        // category
        addTypos("category",
                "catagory", "categroy", "categori", "caegory", "catgory", "cateogry");

        // data
        addTypos("data",
                "dta", "dat", "daat");

        // uses-permission
        addTypos("uses-permission",
                "use-permission", "uses-permision", "uses-permisson",
                "uses-persmission", "uses-permssion", "user-permission", "uses-premission",
                "uses-perimssion", "uses-permision");

        // uses-feature
        addTypos("uses-feature",
                "use-feature", "uses-feture", "uses-featre", "uses-feaure", "user-feature",
                "uses-faeture", "uses-featur");

        // uses-sdk
        addTypos("uses-sdk",
                "use-sdk", "uses-skd", "user-sdk", "uses-dsk", "uses-ssk");

        // uses-library
        addTypos("uses-library",
                "use-library", "uses-libary", "uses-libarary", "user-library",
                "uses-librray", "uses-libray");

        // permission
        addTypos("permission",
                "permision", "permisson", "persmission", "permssion", "premission");

        // permission-group
        addTypos("permission-group",
                "permission-grp", "permision-group", "permission-grup");

        // permission-tree
        addTypos("permission-tree",
                "permission-tre", "permision-tree", "permission-tee");

        // instrumentation
        addTypos("instrumentation",
                "instrumenation", "instrumentaion", "instrumentaton", "instrumntation",
                "instrumantation");

        // meta-data
        addTypos("meta-data",
                "meta-dat", "meta-dta", "metadat", "meata-data", "meta-daat");

        // activity-alias
        addTypos("activity-alias",
                "activity-alais", "activity-alis", "actvity-alias", "activty-alias");

        // grant-uri-permission
        addTypos("grant-uri-permission",
                "grant-uri-permision", "grant-uri-permisson", "grant-url-permission");

        // path-permission
        addTypos("path-permission",
                "path-permision", "path-permisson", "pat-permission");

        // supports-screens
        addTypos("supports-screens",
                "support-screens", "supports-screen", "suports-screens");

        // compatible-screens
        addTypos("compatible-screens",
                "compatible-screen", "compatable-screens", "compatble-screens");

        // supports-gl-texture
        addTypos("supports-gl-texture",
                "supports-gl-texure", "support-gl-texture", "supports-gl-textre");

        // queries
        addTypos("queries",
                "querie", "querys", "quries");

        // package
        addTypos("package",
                "pakage", "packge", "packege", "pacakge");

        // profileable
        addTypos("profileable",
                "profileble", "profilable", "profileabel");
    }

    private static void addTypos(String correct, String... typos) {
        for (String typo : typos) {
            MISSPELLING_TO_CORRECT.put(typo, correct);
        }
    }

    /**
     * Set of all known correct manifest tag names (for case-insensitive matching).
     */
    private static final java.util.Set<String> KNOWN_TAGS;

    static {
        KNOWN_TAGS = new java.util.HashSet<>();
        KNOWN_TAGS.add("manifest");
        KNOWN_TAGS.add("application");
        KNOWN_TAGS.add("activity");
        KNOWN_TAGS.add("service");
        KNOWN_TAGS.add("receiver");
        KNOWN_TAGS.add("provider");
        KNOWN_TAGS.add("intent-filter");
        KNOWN_TAGS.add("action");
        KNOWN_TAGS.add("category");
        KNOWN_TAGS.add("data");
        KNOWN_TAGS.add("uses-permission");
        KNOWN_TAGS.add("uses-feature");
        KNOWN_TAGS.add("uses-sdk");
        KNOWN_TAGS.add("uses-library");
        KNOWN_TAGS.add("permission");
        KNOWN_TAGS.add("permission-group");
        KNOWN_TAGS.add("permission-tree");
        KNOWN_TAGS.add("instrumentation");
        KNOWN_TAGS.add("meta-data");
        KNOWN_TAGS.add("activity-alias");
        KNOWN_TAGS.add("grant-uri-permission");
        KNOWN_TAGS.add("path-permission");
        KNOWN_TAGS.add("supports-screens");
        KNOWN_TAGS.add("compatible-screens");
        KNOWN_TAGS.add("supports-gl-texture");
        KNOWN_TAGS.add("queries");
        KNOWN_TAGS.add("package");
        KNOWN_TAGS.add("profileable");
        KNOWN_TAGS.add("uses-configuration");
        KNOWN_TAGS.add("uses-permission-sdk-23");
        KNOWN_TAGS.add("screen");
        KNOWN_TAGS.add("protected-broadcast");
        KNOWN_TAGS.add("adopt-permissions");
        KNOWN_TAGS.add("original-package");
        KNOWN_TAGS.add("overlay");
        KNOWN_TAGS.add("restrict-update");
        KNOWN_TAGS.add("eat-comment");
        KNOWN_TAGS.add("attribution");
        KNOWN_TAGS.add("processes");
        KNOWN_TAGS.add("process");
        KNOWN_TAGS.add("deny-permission");
        KNOWN_TAGS.add("allow-permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null) {
            return;
        }

        // Check direct misspelling map
        String correct = MISSPELLING_TO_CORRECT.get(tag);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correct)
            );
            return;
        }

        // Check for wrong case (e.g., "Activity" instead of "activity")
        String tagLower = tag.toLowerCase(Locale.US);
        if (!tag.equals(tagLower) && KNOWN_TAGS.contains(tagLower)) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                            tag, tagLower
                    )
            );
        }
    }
}