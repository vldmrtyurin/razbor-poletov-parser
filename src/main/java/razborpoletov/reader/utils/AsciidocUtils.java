package razborpoletov.reader.utils;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.ContentPart;
import org.asciidoctor.ast.StructuredDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by artemvlasov on 26/04/15.
 */
public class AsciidocUtils {
    private static final Asciidoctor asciidoctor = Asciidoctor.Factory.create();
    private static final Logger LOG = LoggerFactory.getLogger(AsciidocUtils.class);
    private static final String TWITTER_PART_NAME = "_twitter";
    private static final List<String> DOCUMENT_IDS = Arrays.asList("_Полезняшки", "_Конференции");


    public static Document parseTwitterPart(File file) {
        StructuredDocument document = asciidoctor.readDocumentStructure(file, new HashMap<>());
        ContentPart part = document.getPartById(TWITTER_PART_NAME);
        String podcastName = file.getName();
        if(part == null) {
            LOG.info("Document {} has no twitter part", podcastName);
        }
        return part == null ? null : Jsoup.parse(part.getContent());
    }

    public static Document parsePartById(File file, String partId) {
        if(!DOCUMENT_IDS.contains(partId)) {
            throw new IllegalArgumentException("Incorrect part id");
        }
        List<ContentPart> parts = asciidoctor.readDocumentStructure(file, new HashMap<>()).getParts();
        ContentPart part = parts.stream()
                .filter(filePart -> {
                    if(filePart.getId() != null) {
                        return Pattern.compile(partId, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher
                                (filePart.getId()).matches();
                    }
                    return false;
                })
                .findFirst()
                .get();
        String podcastName = file.getName();
        if(part == null) {
            LOG.info("Document {} has no {} part", podcastName, partId);
        }
        return part == null ? null : Jsoup.parse(part.getContent());
    }
}
