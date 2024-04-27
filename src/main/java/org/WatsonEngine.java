package org;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.io.File;

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
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilterFactory;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;


public class WatsonEngine {
    boolean indexExists = false;
    String inputFilePath = "";
    Analyzer sa = null;
    Directory index_g = null;

    public WatsonEngine(String inputFile) throws IOException {
        inputFilePath = inputFile;
        buildIndex();
    }

    private void buildIndex() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        StandardAnalyzer analyzer = new StandardAnalyzer();
        Directory index = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        sa = analyzer;
        index_g = index;
        try {
            IndexWriter writer = new IndexWriter(index, config);
            loadData(inputFilePath, writer);


        } catch (IOException ignore) {

        }

        indexExists = true;
    }

    public List<String> queryIt(String query) throws java.io.IOException {
        if (!indexExists) {
            buildIndex();
        }


        try {


            Query q = new QueryParser("content", sa).parse(query);
            int hitsPerPage = 10;
            IndexReader reader = DirectoryReader.open(index_g);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            List<String> ans = new ArrayList<>();

            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                ans.add(d.get("docid"));
//                System.out.println("Doc: " + d.get("docid") + ", Score: " + hits[i].score);
            }
            return ans;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) {
        try {
            String wiki_dir_name = "src/main/resources/wiki-subset-20140602";
            String question_file = "src/main/resources/questions.txt";
            System.out.println("******** Welcome to  Our Engine! ********");
            WatsonEngine objQueryEngine = new WatsonEngine(wiki_dir_name);
            HashMap queries= objQueryEngine.getQueryQuestions(question_file);
            objQueryEngine.computeMRR(queries);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void addDoc(IndexWriter writer, String docName, String text) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("docid", docName, Field.Store.YES));
        doc.add(new TextField("content", text, Field.Store.YES));
        writer.addDocument(doc);

    }

    private static void loadData(String directory, IndexWriter writer) throws Exception {
        File directory_structure = new File(directory);
        File[] files = directory_structure.listFiles();

        if (files == null || files.length == 0) {
            throw new Exception("Directory was not found or is emoty!");
        }

        int total_docs = 0;

        String title = "";
        String category = "";
        String text = "";

        System.out.println("... Loading wiki files: ");

        for (File fileName : files) {
            try (Scanner scanner = new Scanner(fileName)) {
            }
        }

        writer.commit();

        System.out.println("Total Documents Loaded: " + total_docs);
        return;
    }

    private static HashMap<String, String> getQueryQuestions(String file_path) throws IOException {

        System.out.println("... Loading questions file: " + file_path);
        byte[] bytes = Files.readAllBytes(Paths.get(file_path));
        String content = new String(bytes);

        HashMap<String, String> query_to_answer = new HashMap<>();
        String[] parts = content.split("\n" + "\n");

        for (int i = 1; i < parts.length; i++) {
            String[] subParts = parts[i].split("\n", 3);
            if (subParts.length > 1) {
                String category = subParts[0].trim();

                String clue = subParts[1].trim();
                String answer = subParts[2].trim();

                String query =  "+" + String.join(" +", clue.split(" ")); // tweak this part
                query_to_answer.put(query, answer);
            }
        }

        return query_to_answer;
    }

    public double computeMRR(HashMap<String, String> queryAnswers) {
        double mrr = 0;
        int answerPresent = 0;
        int total_queries = queryAnswers.size();

        for (HashMap.Entry<String, String> entry : queryAnswers.entrySet()) {
            String query = entry.getKey();
            String answer = entry.getValue();
            try {
                List<String> answers = queryIt(query);
                if (answers.size() == 0) {
                    continue;
                } else if (answers.contains(answer)) {
                    int rank = answers.indexOf(answer) + 1;

                    mrr += 1 / rank;
                    answerPresent++;
                }
                System.out.println("\nQuery: " + query + "\nAnswer: " + answer + "\nPredictions: " + answers + "\n\n");

            } catch (Exception ignored) {
            }
        }
        double meanmrr = mrr / total_queries;
        System.out.println("\nMRR = " + meanmrr);
        System.out.println("\nAnswer was somewhere in predictions = " + answerPresent + " / " + total_queries);

        return meanmrr;
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
                .addTokenFilter(StopFilterFactory.class, stopMap)
                .addTokenFilter(ASCIIFoldingFilterFactory.class)
                .addTokenFilter(EnglishPossessiveFilterFactory.class)
                .addTokenFilter(KeywordRepeatFilterFactory.class)

                // here is the Porter stemmer step:
                .addTokenFilter(SnowballPorterFilterFactory.class, snowballParams)

                .addTokenFilter(RemoveDuplicatesTokenFilterFactory.class)
                .build();
    }
}