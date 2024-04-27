package org;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.Directory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryEngine {
    boolean indexExists = false;
    Directory index = null;
    StandardAnalyzer analyzer = null;

    public static void main(String[] args ) {
        try {
            System.out.println("******** Welcome to Project Prototyping! ********");
            System.out.println("**************** Creating Index! ****************");
            String[] query = {"information", "retrieval"};
            System.out.println("************** Creating Completed! **************");

            ReadInFiles();

            }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void ReadInFiles() throws IOException {
        // Grab the list of files in the 'resources' directory
        Set<String> files = Stream.of(Objects.requireNonNull(new File("src\\main\\resources\\wiki-subset-20140602")
                        .listFiles()))
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());

        // Iterate through the files
        for (String fileName : files) {
            // Open the file
            File file = new File("src\\main\\resources\\wiki-subset-20140602\\" + fileName);

            byte[] bytes = Files.readAllBytes(file.toPath());
            String content = new String(bytes);

            if (content.contains("GGF President Shri Ghulam Nabi Azad flagged off Gandhi Global Family Youth delegation from Jammu for UK, US and Canada to spread Gandhian message")) {
                System.out.println(fileName);
                return;
            }
        }

    }
}
