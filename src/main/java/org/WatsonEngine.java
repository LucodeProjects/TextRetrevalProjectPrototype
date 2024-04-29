package org;

import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
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


public class WatsonEngine {
    static String WIKI_FILES_DIRECTORY = "src\\main\\resources\\wiki-subset-20140602\\";
    static String QUESTIONS_FILE = "src\\main\\resources\\questions.txt";

    Analyzer analyzer;
    Directory index;

    public WatsonEngine() throws IOException {
        this.analyzer = new MyAnalyzer().get();
        this.index = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(index, config);
        loadData(WIKI_FILES_DIRECTORY, writer);
    }

    public static void main(String[] args) throws IOException, ParseException {
        //System.out.println(availableTokenFilters() + "\n\n\n");

        System.out.println("******** Welcome to our Watson Engine! ********");
        WatsonEngine queryEngine = new WatsonEngine();
        HashMap<String, String> queries = getQueryQuestions(QUESTIONS_FILE);
        queryEngine.computeMRR(queries);
    }

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

    private static void loadData(String directory, IndexWriter writer) throws IOException {
        Set<String> files = Stream.of(Objects.requireNonNull(new File(directory)
                        .listFiles()))
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        System.out.println("... Loading files for indexing");

        String title = "";
        String categories = "";
        String content = "";
        int total_docs = 0;

        /* ----- TEST CODE -----*/

        files.remove("tester.txt");
        //files.clear();
        //files.add("tester.txt");

        /* ---------------------*/

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

        System.out.println("Total Documents Loaded: " + total_docs);
    }

    private static void addDoc(IndexWriter writer, String docName, String text) throws IOException {
        text = docName + " " + text;
        text = text.toLowerCase()
                .replaceAll("!", "")
                .replaceAll("==", " ")
                .replaceAll("--", " ")
                .replaceAll("\\s+", " ");

        Document doc = new Document();
        doc.add(new StringField("title", docName, Field.Store.YES));
        doc.add(new TextField("content", text, Field.Store.YES));

        writer.addDocument(doc);
    }

    private static HashMap<String, String> getQueryQuestions(String file) throws IOException {

        System.out.println("... Loading questions file: " + file);
        HashMap<String, String> query_to_answer = new HashMap<>();

        Scanner scanner = new Scanner(new File(file));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.isEmpty()) {
                String category = line.toLowerCase().trim();
                String clue = scanner.nextLine().trim();
                String answer = scanner.nextLine().trim();

                query_to_answer.put(clue, answer);
            }
        }


        return query_to_answer;
    }

    private void computeMRR(HashMap<String, String> queryAnswers) {
        double mrr = 0;
        int answerPresent = 0;
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
                int rank = answers.indexOf(answer) + 1;

                mrr += (double) 1 / rank;
                answerPresent++;
            }
            System.out.println("\nQuery: " + query + "\nAnswer: " + answer + "\nPredictions: " + answers + "\n");
        }
        double meanmrr = mrr / total_queries;
        System.out.println("\nMRR = " + meanmrr);
        System.out.println("\nAnswer was somewhere in predictions = " + answerPresent + " / " + total_queries);
    }
}

class MyAnalyzer {

    public Analyzer get() throws IOException {

        Map<String, String> snowballParams = new HashMap<>();
        snowballParams.put("language", "English");

        Map<String, String> stopMap = new HashMap<>();
        stopMap.put("words", "stopwords.txt");
        stopMap.put("format", "wordset");

        return CustomAnalyzer.builder()
                .withTokenizer(StandardTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class)
                .addTokenFilter(ASCIIFoldingFilterFactory.class)
                .addTokenFilter(StopFilterFactory.class, stopMap)
                .addTokenFilter(EnglishPossessiveFilterFactory.class)
                .addTokenFilter(HyphenatedWordsFilterFactory.class)
                .addTokenFilter(KeywordRepeatFilterFactory.class)
                .addTokenFilter(SnowballPorterFilterFactory.class, snowballParams)
                .addTokenFilter(RemoveDuplicatesTokenFilterFactory.class)
                .build();
    }
}