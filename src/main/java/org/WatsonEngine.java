package org;

import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.HyphenatedWordsFilterFactory;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilterFactory;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import java.io.File;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * The Watson Engine.
 *
 * @author Aryam Gomez, Amimul Ehsan Zoha, and Muaz Ali
 */
public class WatsonEngine {
   
    static String WIKI_FILES_DIRECTORY = "src" + File.separator + "main" + File.separator + 
    "resources" + File.separator + "wiki-subset-20140602" + File.separator;
    static String QUESTIONS_FILE = "src" + File.separator + "main" + File.separator + 
    "resources" + File.separator + "questions.txt";

    Analyzer analyzer;
    Directory index;

    /**
     * Builds the Watson Engine.
     */
    public WatsonEngine() throws IOException {
        this.analyzer = new MyAnalyzer().get();
        this.index = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(index, config);
        loadData(WIKI_FILES_DIRECTORY, writer);
    }

    /**
     * The main function of the file.
     *
     * @param args - Command inline arguments.
     */
    public static void main(String[] args) throws IOException {
        System.out.println("******** Welcome to our Watson Engine! ********");
        WatsonEngine queryEngine = new WatsonEngine();
        HashMap<String, String> queries = getQueryQuestions(QUESTIONS_FILE);
        queryEngine.computeMRR(queries);
    }

    /**
     * Quarries a query against the index.
     *
     * @param query - A string representing the query.
     * @return - The list of answers to the query.
     */
    private List<String> queryIt(String query) throws ParseException, IOException {
        List<String> ans = new ArrayList<>();

        Query q = new QueryParser("content", analyzer).parse(query);
        int hitsPerPage = 10;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;

        for (ScoreDoc hit : hits) {
            int title = hit.doc;
            Document d = searcher.doc(title);
            ans.add(d.get("title"));
        }

        return ans;
    }

    /**
     * Will load the wiki pages into the index.
     *
     * @param directory - The directory containing the documents that contain the wiki pages.
     * @param writer - The index writer.
     */
    private static void loadData(String directory, IndexWriter writer) throws IOException {
        Set<String> files = Stream.of(Objects.requireNonNull(new File(directory)
                        .listFiles()))
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        System.out.println("... Loading files for indexing from " + directory);

        String title = "";
        String content = "";
        int total_docs = 0;

        for (String file : files) {
            Scanner scanner = new Scanner(new File(directory + file));

            // Process this file
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("[[")) {
                    // If we have already read a title, then we are finished with the last one.
                    if (!title.isEmpty()) {
                        addDoc(writer, title, content);
                        content = "";

                        total_docs++;
                    }

                    // Found page title
                    title = line.trim();
                    title = title.substring(2, title.length() - 2);
                } else {
                    // Found page text
                    content += " " + line;
                }
            }

            // Add last page
            addDoc(writer, title, content);
            total_docs++;
        }

        writer.commit();

        System.out.println("Total Wiki Pages Loaded: " + total_docs);
    }

    /**
     * Will add a document to the index.
     *
     * @param writer - The index writer.
     * @param title - The title of the wiki page.
     * @param text - The text of the wiki page.
     */
    private static void addDoc(IndexWriter writer, String title, String text) throws IOException {
        // Add the title as part of the text
        text = title + " " + text;

        // Do some general cleaning of the text
        text = text.toLowerCase()
                .replaceAll("!", "")
                .replaceAll("==", " ")
                .replaceAll("--", " ")
                .replaceAll("(\\[tpl])|(\\[/tpl])", "")
                .replaceAll("\\s+", " ");

        Document doc = new Document();
        doc.add(new StringField("title", title, Field.Store.YES));
        doc.add(new TextField("content", text, Field.Store.YES));

        writer.addDocument(doc);
    }

    /**
     * Collects the queries to be run.
     *
     * @param file - The path of the file containing the questions.
     * @return - A map of questions to answers.
     */
    private static HashMap<String, String> getQueryQuestions(String file) throws IOException {

        System.out.println("... Loading questions file: " + file + "\n");
        HashMap<String, String> query_to_answer = new HashMap<>();

        Scanner scanner = new Scanner(new File(file));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.isEmpty()) {
                String clue = line.trim() + " " + scanner.nextLine().trim();
                String answer = scanner.nextLine().trim();

                query_to_answer.put(clue, answer);
            }
        }

        return query_to_answer;
    }

    /**
     * Will query all questions and compute performance metrics.
     *
     * @param queryAnswers - A map of questions to answers.
     */
    private void computeMRR(HashMap<String, String> queryAnswers) {
        double mrr = 0;
        int answerPresent = 0;
        int correctAt1 = 0;
        int total_queries = queryAnswers.size();

        for (HashMap.Entry<String, String> entry : queryAnswers.entrySet()) {
            String query = entry.getKey();
            String answer = entry.getValue();
            List<String> answers = new ArrayList<>();

            try {
                String cleanQuery = query.toLowerCase()
                        .replaceAll("!", "")
                        .replaceAll("--", " ")
                        .replaceAll("\\s+", " ").trim();
                answers = queryIt(cleanQuery);
            }
            catch(Exception e) {
                System.out.println("\n--------- Error Unable to Process ---------\n" +
                        query + "\n" +
                        e.getMessage() +
                        "\n---------------------------------------------\n");
            }
            if (answers.isEmpty()) {
                continue;
            } else if (answers.contains(answer)) {
                if (answers.get(0).equals(answer)) {
                    correctAt1++;
                }

                int rank = answers.indexOf(answer) + 1;

                mrr += (double) 1 / rank;
                answerPresent++;
            }
            System.out.println("Query: " + query + "\nAnswer: " + answer + "\nPredictions: " + answers + "\n");
        }
        double meanmrr = mrr / total_queries;
        double pAt1 = (double) correctAt1 / total_queries;

        System.out.println("\nMRR = " + meanmrr);
        System.out.println("P@1 = " + pAt1);
        System.out.println("Answer was somewhere in predictions = " + answerPresent + " / " + total_queries);
    }
}

/**
 * Custom analyzer class.
 */
class MyAnalyzer {

    /**
     * Gets the custom analyzer.
     *
     * @return - The custom analyzer.
     */
    public Analyzer get() throws IOException {

        Map<String, String> snowballParams = new HashMap<>();
        snowballParams.put("language", "English");

        Map<String, String> stopMap = new HashMap<>();
        stopMap.put("words", "stopwords.txt");
        stopMap.put("format", "wordset");

        return CustomAnalyzer.builder()
                .withTokenizer(StandardTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class)
                //.addTokenFilter(StopFilterFactory.class, stopMap)
                .addTokenFilter(HyphenatedWordsFilterFactory.class)
                .addTokenFilter(KeywordRepeatFilterFactory.class)
                .addTokenFilter(SnowballPorterFilterFactory.class, snowballParams)
                //.addTokenFilter(RemoveDuplicatesTokenFilterFactory.class)
                .build();
    }
}