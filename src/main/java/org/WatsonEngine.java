package org;

import java.io.File;
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
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.en.PorterStemFilterFactory;
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
        sa = CustomAnalyzer.builder()
            .withTokenizer(StandardTokenizerFactory.class)
            .addTokenFilter(LowerCaseFilterFactory.class)
            .addTokenFilter(StopFilterFactory.class)
            .addTokenFilter(PorterStemFilterFactory.class)
            .addTokenFilter(ASCIIFoldingFilterFactory.class)
            .addTokenFilter(EnglishPossessiveFilterFactory.class)
            .addTokenFilter(RemoveDuplicatesTokenFilterFactory.class)
            .build();

        index_g = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(sa);
        try (IndexWriter writer = new IndexWriter(index_g, config)) {
            loadData(inputFilePath, writer);
        }
        indexExists = true;
    }

    public List<String> queryIt(String queryText) throws IOException {
        if (!indexExists) {
            buildIndex();
        }
    
        Query q = null;  
        try {
            QueryParser parser = new QueryParser("content", sa);
            q = parser.parse(queryText);
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing query", e);
        }
    
        try (IndexReader reader = DirectoryReader.open(index_g)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            // for LMJelinekMercerSimilarity test
            //float lambda = 0.5f; // Lambda parameter
            if (q == null) {
                throw new IllegalStateException("Query not properly initialized");
            }
            TopDocs docs = searcher.search(q, 10);
            ScoreDoc[] hits = docs.scoreDocs;
    
            List<String> ans = new ArrayList<>();
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                ans.add(d.get("docid"));
            }
            return ans;
        }
    }
    

        public static void main(String[] args) {
        try {
            String wiki_dir_name = "src/main/resources/wiki-subset-20140602";
            String question_file = "src/main/resources/questions.txt";
            System.out.println("******** Welcome to Our Engine! ********");
            WatsonEngine objQueryEngine = new WatsonEngine(wiki_dir_name);
            HashMap queries = objQueryEngine.getQueryQuestions(question_file);
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

    private static void loadData(String directory, IndexWriter writer) throws IOException {
        File directory_structure = new File(directory);
        File[] files = directory_structure.listFiles();
        int total_docs = 0;

        for (File file : files) {
            System.out.println("... Loading file: " + file);
            byte[] bytes = Files.readAllBytes(file.toPath());
            String content = new String(bytes);

            String[] parts = content.split(
                    "\n" +
                            "\n" +
                            "\\[\\[");

            for (int i = 1; i < parts.length; i++) {
                String[] subParts = parts[i].split("]]", 2);
                if (subParts.length > 1) {
                    String docName = subParts[0].trim();
                    String docText = docName + subParts[1].trim();
                    addDoc(writer, docName, docText);
                    total_docs += 1;
                }
            }
        }
        writer.commit();

        System.out.println("Total Documents Loaded: " + total_docs);
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
                String query = category + clue ; // more tweaking needed

               // String query = "+" + String.join(" +", clue.split(" ")); // more tweaking needed
                //query = "+ " + category + " " + query;
                // CATEGORY : TO USE OR NOT TO USE?
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
