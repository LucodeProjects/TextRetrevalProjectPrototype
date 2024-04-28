package org;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

import org.apache.lucene.analysis.standard.StandardAnalyzer;

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

import static org.apache.lucene.analysis.util.TokenFilterFactory.availableTokenFilters;

public class WatsonEngine {
    String WIKI_FILES_DIRECTORY = "src\\main\\resources\\wiki-subset-20140602\\";
    static String QUESTIONS_FILE = "src\\main\\resources\\questions.txt";

    boolean indexExists = false;
    Analyzer analyzer = null;
    Directory index = null;

    private void buildIndex() throws IOException {
        try {
            this.analyzer = new MyAnalyzer().get();
            this.index = new ByteBuffersDirectory();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(index, config);
            loadData(WIKI_FILES_DIRECTORY, writer);
        } catch (Exception ignore) {
        }
        indexExists = true;
    }

    public List<String> queryIt(String query) throws java.io.IOException {
        if (!indexExists) {
            buildIndex();
        }


        try {
            Query q = new QueryParser("content", analyzer).parse(query);
            int hitsPerPage = 10;
            IndexReader reader = DirectoryReader.open(index);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            List<String> ans = new ArrayList<>();

            for (ScoreDoc hit : hits) {
                int docId = hit.doc;
                Document d = searcher.doc(docId);
                ans.add(d.get("docid"));
            }
            return ans;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        try {
            System.out.println(availableTokenFilters() + "\n\n\n");


            System.out.println("******** Welcome to  Our Engine! ********");
            WatsonEngine objQueryEngine = new WatsonEngine();
            HashMap<String, String> queries = getQueryQuestions(QUESTIONS_FILE);
            objQueryEngine.computeMRR(queries);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void addDoc(IndexWriter writer, String docName, String text) throws IOException {
        text = docName + " " + text;
        text = text.toLowerCase().replaceAll("==", " ").replaceAll("\\s+", " ");

        Document doc = new Document();
        doc.add(new StringField("docid", docName, Field.Store.YES));
        doc.add(new TextField("content", text, Field.Store.YES));
        writer.addDocument(doc);

    }

    private static void loadData(String directory, IndexWriter writer) throws Exception {
        Set<String> files = Stream.of(Objects.requireNonNull(new File(directory)
                        .listFiles()))
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        System.out.println("... Loading files for indexing");

        String pageTitle = "";
        String pageCategory = "";
        String pageText = "";
        int total_docs = 0;

        files.remove("tester.txt");
        //files.clear();
        //files.add("tester.txt");

        for (String file : files) {
            try(Scanner scanner = new Scanner(new File(directory + file))) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();

                    if (line.startsWith("[[")) {
                        if (!pageTitle.isEmpty()) {
                            addDoc(writer, pageTitle, pageText);
                            pageText = "";
                        }

                        // found page title
                        pageTitle = line.trim();
                        pageTitle = pageTitle.substring(2, pageTitle.length() - 2);
                    }
                    else {
                        pageText += " " + line;
                    }
                }

                // Add last page
                addDoc(writer, pageTitle, pageText);

                total_docs++;
            }
            catch(Exception ignore) {
            }
        }
        writer.commit();
        System.out.println("Total Documents Loaded: " + total_docs);
    }

    private static HashMap<String, String> getQueryQuestions(String file) throws IOException {

        System.out.println("... Loading questions file: " + file);
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        String content = new String(bytes);

        HashMap<String, String> query_to_answer = new HashMap<>();
        String[] parts = content.split("\n" + "\n");

        for (int i = 1; i < parts.length; i++) {
            String[] subParts = parts[i].split("\n", 3);
            if (subParts.length > 1) {
                String category = subParts[0].trim();

                String clue = subParts[1].trim();
                String answer = subParts[2].trim();

                //String query =  "+" + String.join(" +", clue.split(" ")); // tweak this part
                String query = clue.toLowerCase().trim();
                query_to_answer.put(query, answer);
            }
        }

        return query_to_answer;
    }

    public void computeMRR(HashMap<String, String> queryAnswers) {
        double mrr = 0;
        int answerPresent = 0;
        int total_queries = queryAnswers.size();

        for (HashMap.Entry<String, String> entry : queryAnswers.entrySet()) {
            String query = entry.getKey();
            String answer = entry.getValue();
            try {
                List<String> answers = queryIt(query);
                if (answers.isEmpty()) {
                    continue;
                } else if (answers.contains(answer)) {
                    int rank = answers.indexOf(answer) + 1;

                    System.out.println("$$");
                    mrr += (double) 1 / rank;
                    answerPresent++;
                }
                System.out.println("\nQuery: " + query + "\nAnswer: " + answer + "\nPredictions: " + answers + "\n");

            } catch (Exception ignored) {
            }
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