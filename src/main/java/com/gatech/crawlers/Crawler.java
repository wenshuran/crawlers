package com.gatech.crawlers;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class Crawler {
    private Set<String> visitedUrlSet = new HashSet();
    private LinkedList unvisitedUrlSet = new LinkedList();
    String seed;
    String keyword;
    String savepath;
    String encoding;
    int savenum;
    Analyzer analyzer;
    Directory directory;
    long previousTime = 0L;
    long numKeyExtracted = 0L;
//    long prevNumKeyExtracted = 0L;
//    long prevNumUrlExtracted = 0L;
//    long prevNumUrlUnExtracted = 0L;

    public Crawler(String seed, String keyword, String savepath, int savenum, Analyzer analyzer) throws IOException {
        this.seed = seed;
        this.keyword = keyword;
        this.savepath=savepath;
        this.savenum=savenum;
        this.analyzer=analyzer;
        this.directory = FSDirectory.open(new File(savepath));
    }

    public void run() throws IOException, org.htmlparser.util.ParserException {
        Set<String> seedsSet = new HashSet<>(Collections.singletonList(seed));
        addToUnvisitedUrlSet(seedsSet);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < savenum; i++) {
            if (!unvisitedUrlSet.isEmpty()) {
                String url = unvisitedUrlSet.getFirst().toString();
                unvisitedUrlSet.removeFirst();
                catchPages(url);
                if (System.currentTimeMillis() - previousTime >= 60000){
                    System.out.println(numKeyExtracted);
//                    prevNumKeyExtracted = numKeyExtracted;
                    System.out.println(visitedUrlSet.size());
//                    prevNumUrlExtracted = visitedUrlSet.size();
                    System.out.println(unvisitedUrlSet.size());
//                    prevNumUrlUnExtracted = unvisitedUrlSet.size();
                    previousTime = System.currentTimeMillis();
                }
            }
        }
        System.out.println((System.currentTimeMillis() - startTime)/1000);
    }

    public void catchPages(String url) throws IOException, org.htmlparser.util.ParserException {
        HttpClient httpClient=new HttpClient();
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
        GetMethod getMethod;
        try{
            getMethod = new GetMethod(url);
            getMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
            getMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                    new DefaultHttpMethodRetryHandler());
            int statusCode = httpClient.executeMethod(getMethod);
            if(statusCode!= HttpStatus.SC_OK){
                System.err.print("GET Method Failed:"+url+getMethod.getStatusLine());
            }else{
                encoding = getMethod.getResponseCharSet();
                createIndex(url);
                visitedUrlSet.add(url);
                addToUnvisitedUrlSet(getUrls(url));
            }
        }catch (Exception ignored){
        }
    }

    private void createIndex(String url) throws IOException, org.htmlparser.util.ParserException {
        String content="";
        content=getContentByUrl(url);
        numKeyExtracted += countKeyword(content);
        Document doc = new Document();
        doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LATEST, analyzer);
        IndexWriter indexWriter = new IndexWriter(directory, iwc);
        indexWriter.addDocument(doc);
        indexWriter.close();
    }

    private String getContentByUrl(String url) throws org.htmlparser.util.ParserException {
        StringBuilder content = new StringBuilder();
        Parser parser=new Parser(url);
        Node nodes;
        for(NodeIterator iterator = parser.elements(); iterator.hasMoreNodes();){
            nodes=iterator.nextNode();
            content.append(nodes.toPlainTextString().replaceAll("\n", ""));
        }
        return content.toString();
    }

    public int countKeyword(String content){
        int index = 0;
        int count = 0;
        while((index = content.indexOf(keyword, index)) != -1) {
            count++;
            index = index + keyword.length();
        }
        return count;
    }

    public Set<String> getUrls(String url) throws org.htmlparser.util.ParserException {
        Set<String> links= new HashSet<>();
        Parser parser;
        parser = new Parser(url);
        parser.setEncoding(encoding);
        NodeFilter frameFilter= node -> node.getText().startsWith("frame src=");
        OrFilter linkFilter=new OrFilter(new NodeClassFilter(LinkTag.class),frameFilter);
        NodeList list=parser.extractAllNodesThatMatch(linkFilter);
        for(int i=0;i<list.size();i++){
            Node tag=list.elementAt(i);
            if(tag instanceof LinkTag){
                LinkTag link=(LinkTag)tag;
                String linkUrl=link.getLink();
                if(frameFilter.accept(tag)){
                    String frameTxt=tag.getText();
                    int start=frameTxt.indexOf("src=");
                    frameTxt=frameTxt.substring(start);
                    int end=frameTxt.indexOf(" ");
                    if(end==-1){
                            end=frameTxt.indexOf(">");
                    }
                    String frameUrl=frameTxt.substring(5,end-1);
                    if(frameUrl.startsWith(seed))
                        links.add(frameUrl);
                }else{
                    if(linkUrl.startsWith(seed)){
                        links.add(linkUrl);
                    }
                }
            }
        }
        return links;
    }

    public  void addToUnvisitedUrlSet(Set<String> urls){
        for (String url : urls) {
            if(!isVisited(url)){
                unvisitedUrlSet.add(url);
            }
        }
    }
    public boolean isVisited(String url){
        boolean isVisited=false;
        for (String visitedUrl : visitedUrlSet) {
            if (visitedUrl.equals(url)) {
                isVisited = true;
                break;
            }
        }
        return isVisited;
    }

}



