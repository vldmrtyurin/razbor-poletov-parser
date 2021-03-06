package razborpoletov.reader.parsers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import razborpoletov.reader.entity.UsefulThing;
import razborpoletov.reader.utils.AsciidocUtils;
import razborpoletov.reader.utils.MarkdownUtils;
import razborpoletov.reader.utils.PodcastFileUtils;
import razborpoletov.reader.utils.UrlUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static razborpoletov.reader.utils.Constants.*;

/**
 * Created by artemvlasov on 20/05/15.
 */
public class UsefulThingParser implements Parser{
    private static final Logger LOG = LoggerFactory.getLogger(UsefulThingParser.class);
    private List<String> tags;
    private Map<String, List<String>> duplicateTags;

    public UsefulThingParser() throws IOException {
        localParseTags();
    }

    public List<UsefulThing> parse(List<File> files, boolean asciidocOnly) throws IOException,
            URISyntaxException {
        List<UsefulThing> usefulThings = new ArrayList<>();
        for (File file : files) {
            if(Pattern.matches("20([0-9]{2}-){3}episode-[0-9].+", file.getName())) {
                if(asciidocOnly) {
                    usefulThings.addAll(parseAsciidoc(file));
                } else {
                    switch (FilenameUtils.getExtension(file.getName())) {
                        case ASCII_DOC:
                            usefulThings.addAll(parse(AsciidocUtils.parsePartById(file, "_Полезняшки").getElementsByTag("a"),
                                    PodcastFileUtils.getPodcastId(file)));
                            break;
                        case MARKDOWN_FORMAT:
                            usefulThings.addAll(parse(Jsoup.parse(MarkdownUtils.parseToHtml(file)), PodcastFileUtils
                                    .getPodcastId(file)));
                            break;
                        case HTML:
                            usefulThings.addAll(parse(file));
                            break;
                    }
                }
            }
        }
        return usefulThings;
    }

    public List<UsefulThing> parse(File file) throws IOException, URISyntaxException {
        Pattern pattern = Pattern.compile(".+(?=(a*(sc)?i*(doc)?d?))\\.a*(sc)?i*(doc)?d?");
        Matcher matcher = pattern.matcher(file.getName());
        if(matcher.matches()) {
            return parseAsciidoc(file);
        }
        return parse(Jsoup.parse(file, "UTF-8"), PodcastFileUtils.getPodcastId(file));
    }
    public List<UsefulThing> parseAsciidoc(File file) throws IOException, URISyntaxException {
        if(AsciidocUtils.parsePartById(file, "_Полезняшки") == null) {
            return null;
        }
        return parse(AsciidocUtils.parsePartById(file, "_Полезняшки").getElementsByTag("a"), PodcastFileUtils.getPodcastId
                (file));
    }
    private List<UsefulThing> parse(Document document, long podcastId) throws IOException, URISyntaxException {
        String html = document.html();
        Elements elements = null;
        if(html.contains("Полезняшка") || html.contains("Полезняшки")) {
            for(Element element : document.getElementsByTag("li")) {
                if(element.text().contains("Полезняшка") || element.text().contains("Полезняшки")) {
                    elements = element.getElementsByTag("a");
                }
            }
        }
        return elements != null ? parse(elements, podcastId) : null;
    }

    private List<UsefulThing> parse(Elements elements, long podcastId) {
        List<UsefulThing> usefulStuffs = new ArrayList<>();
        for(Element element : elements) {
            UsefulThing usefulStuff = new UsefulThing();
            String url = element.attributes().get("href");
            int responseCode;
            responseCode = UrlUtils.checkUrlStatus(url);
            if("http://razbor-poletov.com".equals(url)) {
                return usefulStuffs;
            }
            if(!Pattern.matches("(^(4|5)([0-9]{2})$)|0", String.valueOf(responseCode)) && !url.contains("razbor-poletov")) {
                try {
                    setUsefulThingContent(usefulStuff, url, podcastId);
                    usefulStuffs.add(usefulStuff);
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                    LOG.info(e.getMessage());
                }
            } else {
                LOG.info("Url {} return error with status {}", url, responseCode);
            }
        }
        return usefulStuffs;
    }

    /**
     * Parse popular tags from the file in classpath
     * @throws IOException
     */
    private void localParseTags() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        duplicateTags = objectMapper.readValue(getClass().getResourceAsStream(DUPLICATE_TAGS),
                new TypeReference<Map<String, List<String>>>() {});
        tags = objectMapper.readValue(getClass().getResourceAsStream(TAGS),
                new TypeReference<List<String>>() {});
    }

    /**
     * Set data to entity UsefulThing name, ulr, tags and description. If url is not github url it will create name
     * from the url and get description of the project from github.
     * @param usefulThing
     * @param url
     * @throws IOException
     * @throws URISyntaxException
     */
    private void setUsefulThingContent(UsefulThing usefulThing, String url, long podcastId) throws IOException,
            URISyntaxException {
        String name = null;
        if(url.contains("github")) {
            if(url.lastIndexOf("/") != url.length() - 1 && !url.substring(url.lastIndexOf
                    ("/"), url.length()).contains("github")) {
                String substringName = url.substring(url.lastIndexOf("/") + 1, url.length());
                if (substringName.length() != 0) {
                    name = substringName;
                    usefulThing.setDescription(UrlUtils.getGithubDescription(url));
                }
            }
        }
        if(name == null) {
            if (url.contains("http") || url.contains("https") || url.contains("www")) {
                if (url.contains("www.")) {
                    name = url.substring(url.indexOf("www.") + "www.".length())
                            .split("\\.")[0];
                } else {
                    name = url.substring(url.indexOf("://") + "://".length())
                            .split("\\.")[0];
                }
            }
            try {
                String githubUrl = UrlUtils.findGithubLink(url);
                if(githubUrl != null) {
                    usefulThing.setDescription(UrlUtils.getGithubDescription(githubUrl));
                }
            } catch (IOException | URISyntaxException | UnsupportedOperationException e ) {
                LOG.warn(e.getMessage());
            }

        }
        usefulThing.setPodcastId(podcastId);
        usefulThing.setTags(parseTags(url));
        usefulThing.setName(name);
        usefulThing.setLink(url);
    }

    /**
     * Parse tags from the web site content. Method check status of the link if it's available. If link is github
     * link, it will get special part of HTML, that contains useful information about link.
     * This method will return top 5 common tags.
     * @param url
     * @return List of tags
     * @throws IOException
     * @throws URISyntaxException
     */
    private List<String> parseTags(String url) throws IOException, URISyntaxException {
        List<String> tags = null;
        if(!Pattern.matches("(^(4|5)([0-9]{2})$)|0", String.valueOf(UrlUtils.checkUrlStatus(url)))) {
            Connection connection = Jsoup.connect(url);
            Document document = connection.get();
            final Element element = Pattern.compile("https?://github.com/.+/.+").matcher(url).matches() ? document
                    .getElementById("readme") : document.body();
            if(element != null) {
                tags = this.tags.stream()
                        .filter(tag ->
                                element.getElementsMatchingOwnText(Pattern.compile("^(" + tag + ")$", Pattern
                                        .CASE_INSENSITIVE | Pattern.MULTILINE)).size() > 0 || (duplicateTags.containsKey(tag) &&
                                        duplicateTags
                                                .get(tag).stream().anyMatch(dt -> element.getElementsMatchingOwnText(Pattern.compile
                                                (dt, Pattern.CASE_INSENSITIVE)).size() > 0)))
                        .distinct()
                        .sorted(Comparator.comparing(tag -> -element.getElementsMatchingOwnText(Pattern.compile("^(" + tag + ")$", Pattern
                                .CASE_INSENSITIVE | Pattern.MULTILINE)).size()))
                        .limit(5)
                        .collect(Collectors.toList());
            } else {
                LOG.warn("{} url not contains html body");
            }

        }
        return tags;
    }
}
