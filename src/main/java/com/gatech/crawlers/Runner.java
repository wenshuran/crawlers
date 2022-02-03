package com.gatech.crawlers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.IOException;

public class Runner {


    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        String seed = "https://www.cc.gatech.edu";
        String keyword = "Computational Science and Engineering";
        String savepath = "./output/";
        int savenum = 1000;
        Analyzer analyzer=new StandardAnalyzer();
        Crawler crawler=new Crawler(seed, keyword, savepath, savenum, analyzer);
        try {
            crawler.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}