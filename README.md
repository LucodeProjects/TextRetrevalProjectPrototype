<h1>483/583 Final Project</h1>
<h4>Aryam Gomez, Amimul Ehsan Zoha, and Muaz Ali</h4>

# Commands to Run the Project

Watson Engine:
The simplest is to load the project in IntelliJ IDEA and run the WatsonEngine.java file.

To run our project for the undergraduate version please click: ``Watson Engine`` Or travel to the WatsonEngine.java file and "Run" the current file.


To access the LLM based re-evaluation of the Engine's responses (graduate version) please open the following notebook: ``LLMEvaluation.ipynb``

Alternatively:
java -classpath {all the libraries needed} org.WatsonEngine, or you can also access the README.md file and use the Run option from there.


LLM Evaluation:
Open the JupyterNotebook named as LLMEvaluation.ipynb to access the LLM evaluation part.

The current configuration is set to the combination which gives the best results for our core implementation: 
TPL Tags Removed, Stop Words Removed, Using Porter Stemmer, Duplicates Included 

If you want to change the configurations, you can modify the Custom Analyzer: MyAnalyzer by commenting out the options. For example, if you want to see the scores without using Porter Stemmer, do: //.addTokenFilter(StopFilterFactory.class, stopMap)in line 293 of WatsonEngine class. 

