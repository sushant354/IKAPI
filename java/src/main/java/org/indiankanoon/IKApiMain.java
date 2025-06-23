package org.indiankanoon;

import com.opencsv.CSVWriter;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class IKArgParser
{
    public static ArgumentParser  getArgParser()
    {
        ArgumentParser parser = ArgumentParsers.newFor("IKApi")
                .build()
                .defaultHelp(true)
                .description("For downloading from the api.indiankanoon.arg endpoint");

        parser.addArgument("-l","--loglevel")
                .dest("loglevel")
                .required(false)
                .setDefault("info")
                .help("log level(severe [for error/critical] || warning || info || finest [for debug])");

        parser.addArgument("-g","--logfile")
                .dest("logfile")
                .required(false)
                .help("log file");

        parser.addArgument("-c","--doctype")
                .dest("doctype")
                .required(false)
                .help("doctype");

        parser.addArgument("-f","--fromdate")
                .dest("fromdate")
                .required(false)
                .help("from date in DD-MM-YYYY format");

        parser.addArgument("-t","--todate")
                .dest("todate")
                .required(false)
                .help("to date in DD-MM-YYYY format");

        parser.addArgument("-S","--sortby")
                .dest("sortby")
                .required(false)
                .help("sort results by (mostrecent|leastrecent)");

        parser.addArgument("-D","--datadir")
                .dest("datadir")
                .required(true)
                .help("directory to store files");

        parser.addArgument("-s","--sharedtoken")
                .dest("token")
                .required(true)
                .help("api.ik shared token");

        parser.addArgument("-q","--query")
                .dest("q")
                .required(false)
                .help("ik query");

        parser.addArgument("-Q","--qfile")
                .dest("qfile")
                .required(false)
                .help("queries in a file");

        parser.addArgument("-d","--docid")
                .type(Integer.class)
                .dest("docid")
                .required(false)
                .help("ik docid");

        parser.addArgument("-o","--original")
                .dest("orig")
                .action(Arguments.storeTrue())
                .required(false)
                .help("ik original");

        parser.addArgument("-m","--maxcites")
                .type(Integer.class)
                .dest("maxcites")
                .setDefault(0)
                .required(false)
                .help("doc maxcites");

        parser.addArgument("-M","--maxcitedby")
                .type(Integer.class)
                .dest("maxcitedby")
                .setDefault(0)
                .required(false)
                .help("doc maxcitedby");

        parser.addArgument("-p","--maxpages")
                .type(Integer.class)
                .dest("maxpages")
                .setDefault(1)
                .required(false)
                .help("max search result pages");

        parser.addArgument("-P","--pathbysrc")
                .dest("pathbysrc")
                .action(Arguments.storeTrue())
                .required(false)
                .help("save docs by src");

        parser.addArgument("-a","--addedtoday")
                .dest("addedtoday")
                .action(Arguments.storeTrue())
                .required(false)
                .setDefault(false)
                .help("Search only the documents that were added today");

        parser.addArgument("-N","--workers")
                .type(Integer.class)
                .dest("numworkers")
                .setDefault(5)
                .required(false)
                .help("num workers for parallel downloads");

        return parser;
    }
}

class FileStorage
{
    private String datadir;
    private static final Logger fileStorageLogger  = Logger.getLogger("filestorage");

    public FileStorage(String datadir)
    {
        this.datadir = datadir;

    }

    public String getJsonPath(String q) {
          Path jsonPath = Paths.get(datadir,q+".json");
          return jsonPath.toString();
    }

    public boolean saveJson(String jsonStr, String filePath) {
        boolean success;
        try(BufferedWriter bw =new BufferedWriter(
                new FileWriter(filePath,StandardCharsets.UTF_8)))
        {
            bw.write(jsonStr);
            success =true;
        } catch (IOException e) {
            success =false;
        }
        return success;
    }

    public String[] getJsonOrigPath(String dataDir, Integer docId) {
        Path jsonPath = Paths.get(dataDir,String.format("%d.json",docId));
        Path origPath = Paths.get(dataDir,String.format("%d_orignal",docId));
        return new String[]{jsonPath.toString(),origPath.toString()};
    }

    public boolean exists(String filePath) {
        return new File(filePath).exists();
    }

    public boolean existsOriginal(String origPath) {
        File file = new File(origPath);
        File dir = file.getParentFile();
        String baseName = file.getName();

        if(dir != null && dir.isDirectory())
        {
            File[] matches = dir.listFiles((d, name) -> name.startsWith(baseName));
            return matches != null && matches.length > 0;
        }
        return false;
    }

    public boolean saveOriginal(String orig, String origPath) {
        JSONObject obj;
        try
        {
            obj = new JSONObject(orig);
        } catch (JSONException e) {
            fileStorageLogger.warning(String.format("Original is not a correct json %s",e.getMessage()));
            return false;
        }
        if(obj.has("errmsg"))
        {
            return false;
        }
        try
        {
            byte[] doc = Base64.getDecoder().decode(obj.getString("doc"));
            String extension = getFileExtension( obj.getString("Content-Type"));
            String filePath = origPath+"."+extension;
            try(FileOutputStream fos = new FileOutputStream(filePath))
            {
                fos.write(doc);
            }
            return true;
        } catch (Exception e) {
            fileStorageLogger.warning("Error processing file: "+ e.getMessage());
            return false;
        }
    }

    private String getFileExtension(String mtype) {
        String t = "unkwn";
        if(mtype == null || mtype.isEmpty())
        {

        } else if (Pattern.compile("text/html").matcher(mtype).find()) {
            t="html";
        } else if (Pattern.compile("application/postscript").matcher(mtype).find())
        {
            t="ps";
        } else if (Pattern.compile("application/pdf").matcher(mtype).find())
        {
            t="pdf";   
        } else if (Pattern.compile("text/plain").matcher(mtype).find()) {
            t= "txt";
        } else if (Pattern.compile("image/png").matcher(mtype).find()) {
            t="png";
        }
        return t;
    }

    public Path getSearchPath(String q) {
        Path dataDir = Paths.get(this.datadir,q);
        IKApiMain.mkDir(dataDir);
        return dataDir;
    }

    public List<Object> getToCWriter(Path dataDir) throws IOException {
        String[] header = {"position","docid","date","court","title"};
        Path tocFile = dataDir.resolve("toc.csv");

        Writer handler = Files.newBufferedWriter(tocFile,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);

        CSVWriter csvWriter = new CSVWriter(handler);
        csvWriter.writeNext(header);
        return Arrays.asList(handler,csvWriter);
    }

    public Path getDocPath(String docSource, String publishDate) throws Exception {
        Path dataDir = Paths.get(this.datadir,docSource);
        IKApiMain.mkDir(dataDir);
        LocalDate date = IKApiMain.getDateObj(publishDate);
        dataDir = Paths.get(dataDir.toString(),String.valueOf(date.getYear()));
        IKApiMain.mkDir(dataDir);
        Path docPath = Paths.get(dataDir.toString(),date.toString());
        IKApiMain.mkDir(docPath);
        return docPath;
    }

    public Path getDocpathByPosition(Path dataDir, int current) {
        Path docPath = Paths.get(dataDir.toString(),String.valueOf(current));
        IKApiMain.mkDir(docPath);
        return docPath;
    }
}

class IKApi
{
    private static final Logger ikApiLogger = Logger.getLogger("ikapi");

    private Map<String,String> headers;
    private  String baseHost;
    private FileStorage storage;
    private Integer maxCites;
    private Integer maxCitedBy;
    private Boolean orig;
    private Integer maxPages;
    private Boolean pathBySrc;
    private BlockingQueue<String> queue;
    private Integer numWorkers;
    private Boolean addedToday;
    private String fromDate;
    private  String toDate;
    private  String sortBy;

    public IKApi(Namespace ns, FileStorage fileStorage)
    {
        this.headers = Map.of(
                "Authorization", String.format("Token %s", ns.getString("token")),
                "Accept", "application/json"
        );
        this.baseHost = "api.indiankanoon.org";
        this.storage = fileStorage;
        this.maxCites = ns.getInt("maxcites");
        this.maxCitedBy = ns.getInt("maxcitedby");
        this.orig = ns.getBoolean("orig");
        this.maxPages = ns.getInt("maxpages");
        this.pathBySrc = ns.getBoolean("pathbysrc");
        this.queue = new LinkedBlockingQueue<>(20);
        this.numWorkers = ns.getInt("numworkers");
        this.addedToday = ns.getBoolean("addedtoday");
        this.fromDate = ns.getString("fromdate");
        this.toDate = ns.getString("todate");
        this.sortBy = ns.getString("sortby");

        if(this.maxPages > 100)
        {
            this.maxPages = 100;
        }
    }


    public boolean saveDocFragment(Integer docId, String query) throws Exception {
        boolean success = false;
        String jsonStr = fetchDocFragment(docId,query);

        if (jsonStr == null || jsonStr.isEmpty())
        {
            return success;
        }

        String jsonPath = this.storage.getJsonPath(String.format("%d q: %s",docId,query));
        success = this.storage.saveJson(jsonStr,jsonPath);
        return success;

    }

    private String fetchDocFragment(Integer docId, String query) throws Exception{
        String encodedQuery = URLEncoder.encode(query,"UTF-8");
        String url = String.format("/docfragment/%d/?formInput=%s",docId,encodedQuery);
        return callApi(url);
    }

    private String callApi(String url)  {
        int count = 0;
        String results =null;

        while(count < 3){
            try {
                results = callApiDirect(url);
                if (results == null ||  Pattern.compile("error code:").matcher(results).find())

                {
                    ikApiLogger.warning(String.format("Error in call_api %s %s",url,results));
                    count++;
                    Thread.sleep(count * 10_000L);
                    continue;
                }
                break;
            } catch (Exception e) {
                ikApiLogger.warning(String.format("Error in call_api %s %s",url,e.getMessage()));
                count++;
                try {
                    Thread.sleep(count * 10_000L);
                } catch (InterruptedException ie) {
                    ikApiLogger.warning(String.format("Error in call_api %s %s",url,ie.getMessage()));
                    Thread.currentThread().interrupt();
                    break;
                }}}
        return results;
    }

    private String callApiDirect(String endPoint) throws Exception {
        URI uri = URI.create("https://" + this.baseHost + endPoint);
        URL url = uri.toURL();
        HttpsURLConnection  connection = (HttpsURLConnection)  url.openConnection();
        connection.setRequestMethod("POST");
        for (Map.Entry<String, String> header : this.headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        connection.setDoOutput(true);
        connection.connect();
        String result;
        try(InputStream is = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(is,StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr))
        {
            StringBuilder response = new StringBuilder();
            String line;
            while((line=br.readLine()) !=null)
            {
                response.append(line);
            }
            result = response.toString();
        } catch (IOException ie)
        {
            InputStream  errorStream = connection.getErrorStream();
            if (errorStream != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    result = errorResponse.toString();
                }
            }
            else {
                result = null;
            }
        }
        return result;
    }

    public boolean downloadDoc(Integer docId, String dataDir) {
        boolean success = false;
        boolean orig_needed = this.orig;
        String[] paths= this.storage.getJsonOrigPath(dataDir,docId);
        String jsonPath = paths[0];
        String origPath = paths[1];

        if(!this.storage.exists(jsonPath))
        {
            String jsonStr = fetchDoc(docId);
            JSONObject d;
            try
            {
                d = new JSONObject(jsonStr);
            } catch (JSONException e) {
                ikApiLogger.severe(String.format("Error in getting doc %d %s",docId,e.getMessage()));
                return success;
            }

            if(d.has("errmsg"))
            {
                ikApiLogger.severe(String.format("Error in getting doc %d",docId));
                return success;
            }

            ikApiLogger.info(String.format("Saved %s",d.optString("title","untitled")));
            this.storage.saveJson(jsonStr,jsonPath);
            success = true;

            if(orig_needed)
            {
                if(!d.optBoolean("courtcopy",false))
                {
                    orig_needed = false;
                }
            }
        }

        if(orig_needed && !this.storage.existsOriginal(origPath))
        {
            String orig = fetchOrigDoc(docId);
            if( orig != null &&  this.storage.saveOriginal(orig,origPath))
            {
                ikApiLogger.info(String.format("Saved original %d",docId));
            }
        }
        return success;
    }

    private String fetchOrigDoc(Integer docId) {
        String url = String.format("/origdoc/%d/",docId);
        return callApi(url);
    }

    private String fetchDoc(Integer docId) {
        String url = String.format("/doc/%d/",docId);
        List<String> queryParams = new ArrayList<>();
        if(maxCites>0)
        {
            queryParams.add(String.format("maxcites=%d",maxCites));
        }
        if(maxCitedBy>0)
        {
            queryParams.add(String.format("maxcitedby=%d",maxCitedBy));
        }
        if(!queryParams.isEmpty())
        {
            url +=  "?"+String.join("&",queryParams);
        }
        return callApi(url);
    }

    public List<Integer> saveSearchResults(String q) {
        List<Integer> docids = new ArrayList<>();
        try {
            Path dataDir = this.storage.getSearchPath(q);
            List<Object> result = this.storage.getToCWriter(dataDir);

            Writer handler = (Writer) result.get(0);
            CSVWriter writer = (CSVWriter) result.get(1);

            int pageNum = 0;
            int current = 1;
            
            while (true)
            {
                String results =  search(q,pageNum,this.maxPages);
                JSONObject obj = new JSONObject(results);
                if(obj.has("errmsg"))
                {
                    ikApiLogger.warning(String.format("Error: %s, pagenum: %d q: %s",obj.getString("errmsg"),pageNum,q));
                    break;
                }
                if (!obj.has("docs")) {
                    break;
                }

                JSONArray docs = obj.getJSONArray("docs");
                if(docs.isEmpty())
                {
                    break;
                }
                ikApiLogger.warning(String.format("Num results: %d , pagenum: %d found: %s q: %s",docs.length(),pageNum,obj.getString("found"),q));

                for(int i=0;i<docs.length();i++)
                {
                    JSONObject doc = docs.getJSONObject(i);
                    String docId = String.valueOf(doc.get("tid"));
                    String title = doc.getString("title");
                    String publishDate = doc.getString("publishdate");
                    String court =  doc.getString("docsource");
                    String[] tocRow = { String.valueOf(current),docId,publishDate,court,title};

                    writer.writeNext(tocRow);

                    Path docPath;

                    if(pathBySrc )
                    {
                        docPath = this.storage.getDocPath(court,publishDate);
                    }
                    else
                    {
                        docPath = this.storage.getDocpathByPosition(dataDir,current);
                    }

                    if(downloadDoc(Integer.parseInt(docId),docPath.toString()))
                    {
                        docids.add(Integer.parseInt(docId));
                    }
                    current ++;
                }
                handler.flush();
                pageNum += maxPages;
            }
            handler.close();
        } catch (Exception e) {
            ikApiLogger.severe("Exception while saving search results: "+e.getMessage());
        }
        return docids;
    }

    private String search(String q, int pageNum, Integer maxPages) {
        String encodedQuery = URLEncoder.encode(q,StandardCharsets.UTF_8);
        String url = String.format("/search/?formInput=%s&pagenum=%d&maxpages=%d",encodedQuery,pageNum,maxPages);
        return callApi(url);
    }

    public List<Integer> downloadDocType(String docType) throws Exception {
        String q = String.format("doctypes: %s",docType);
        q = makeQuery(q);

        int pageNum = 0;
        List<Integer> docIds = new ArrayList<>();
        JSONObject obj;
        while (true)
        {
            String results = search(q,pageNum,this.maxPages);
            obj = new JSONObject(results);
            JSONArray docs = obj.getJSONArray("docs");
            if(!obj.has("docs") || docs.isEmpty() )
            {
                break;
            }
            ikApiLogger.warning(String.format("Num results: %d, pagenum: %d",docs.length(),pageNum));

            for(int i=0;i<docs.length();i++)
            {
                JSONObject doc = docs.getJSONObject(i);
                String docSource = doc.getString("docsource");
                String publishDate = doc.getString("publishdate");
                String tid = String.valueOf(doc.get("tid"));

                Path docPath = this.storage.getDocPath(docSource,publishDate);
                if(downloadDoc(Integer.parseInt(tid),docPath.toString()))
                {
                    docIds.add(Integer.parseInt(tid));
                }
            }
            pageNum += this.maxPages;
        }
        return docIds;
    }

    private String makeQuery(String q) {
        StringBuilder qs =new StringBuilder(q);
        if(this.fromDate != null && !this.fromDate.isEmpty())
        {
            qs.append(String.format(" fromdate: %s",this.fromDate));
        }
        if(this.toDate != null && !this.toDate.isEmpty())
        {
            qs.append(String.format(" todate: %s",this.toDate));
        }
        if(this.addedToday)
        {
            qs.append(" added:today");
        }
        if(this.sortBy != null && !this.sortBy.isEmpty());
        {
            qs.append(" sortby: "+this.sortBy);
        }

        return qs.toString();
    }

    public void executeTasks(List<String> queries) {
        ExecutorService executor = Executors.newFixedThreadPool(this.numWorkers);
        for(int i=0;i<this.numWorkers;i++)
        {
            executor.submit(this::worker);
        }

        for(String query: queries)
        {
            this.queue.add(makeQuery(query));
        }
        for(int i=0;i<this.numWorkers;i++)
        {
            queue.add("__POISON__");
        }

        executor.shutdown();
        try
        {
            if(!executor.awaitTermination(Long.MAX_VALUE,TimeUnit.SECONDS))
            {
                ikApiLogger.warning("Executor did not terminate cleanly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ikApiLogger.severe("Thread interrupted while waiting for termination.");
        }
    }

    private void worker() {
          try {
              while (true) {
                  String q = this.queue.take();
                  if ("__POISON__".equals(q)) {
                      break;
                  }
                  ikApiLogger.info("Processing " + q);
                  saveSearchResults(q);
                  ikApiLogger.info("Done with query " + q);
              }
          }
          catch (InterruptedException ie)
          {
              Thread.currentThread().interrupt();
              ikApiLogger.severe("Worker thread interrupted while waiting for a task: " + ie.getMessage());
          }
    }
}
public class IKApiMain {

    private static final String LOG_FORMAT = "%1$tF %1$tT: %2$s: [%5$s.%6$s:%7$d]: %3$s %4$s %n";

    private static  final Logger ikApiLogger = Logger.getLogger("ikapi");

    public static void mkDir(Path filePath)
    {
     if(!Files.exists(filePath))
     {
         try
         {
             Files.createDirectory(filePath);
         } catch (IOException e) {
             ikApiLogger.warning("Exception thrown for requested directory of filePath "+filePath+" also exception message: "+e.getMessage());
         }
     }
    }
    public static void initializeStreamLogging(Level level) {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        removeDefaultHandlers(rootLogger);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord record) {
                String className = record.getSourceClassName();
                String methodName = record.getSourceMethodName();
                int lineNumber = -1;

                // Attempt to extract line number using stack trace
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                for (StackTraceElement element : stackTrace) {
                    if (element.getClassName().equals(className) &&
                        element.getMethodName().equals(methodName)) {
                        lineNumber = element.getLineNumber();
                        break;
                    }
                }

                return String.format(LOG_FORMAT,
                        new Date(record.getMillis()),                  // %1$ = timestamp
                        record.getLoggerName(),                        // %2$ = logger name
                        record.getLevel().getLocalizedName(),          // %3$ = log level
                        record.getMessage(),                           // %4$ = log message
                        className != null ? className : "UnknownClass",// %5$ = class name
                        methodName != null ? methodName : "UnknownMethod", // %6$ = method name
                        lineNumber        
                        );
            }
        });
        rootLogger.addHandler(handler);
    }

    public static void initializeFileLogging(Level level, String filePath) throws Exception{
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        removeDefaultHandlers(rootLogger);

        FileHandler handler = new FileHandler(filePath, false); // false = overwrite
        handler.setEncoding(StandardCharsets.UTF_8.name());
        handler.setLevel(level);
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord record) {
                String className = record.getSourceClassName();
                String methodName = record.getSourceMethodName();
                int lineNumber = -1;

                // Attempt to extract line number using stack trace
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                for (StackTraceElement element : stackTrace) {
                    if (element.getClassName().equals(className) &&
                        element.getMethodName().equals(methodName)) {
                        lineNumber = element.getLineNumber();
                        break;
                    }
                }

                return String.format(LOG_FORMAT,
                        new Date(record.getMillis()),                  // %1$ = timestamp
                        record.getLoggerName(),                        // %2$ = logger name
                        record.getLevel().getLocalizedName(),          // %3$ = log level
                        record.getMessage(),                           // %4$ = log message
                        className != null ? className : "UnknownClass",// %5$ = class name
                        methodName != null ? methodName : "UnknownMethod", // %6$ = method name
                        lineNumber       
                        );
            }
        });
        rootLogger.addHandler(handler);
    }

    private static void removeDefaultHandlers(Logger logger) {
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
    }

    private static void setUpLogging(String level, String fileName) throws Exception
    {
        Map<String,Level>  levelMap = Map.of(
                "severe", Level.SEVERE,
                "warning", Level.WARNING,
                "info", Level.INFO,
                "finest", Level.FINEST);

        Level loglevel = levelMap.get(level);
        if(fileName != null && !fileName.isEmpty())
        {
            initializeFileLogging(loglevel,fileName);
        }
        else {
            initializeStreamLogging(loglevel);
        }
    }
    public static void main(String[] args)
    {
        try{
        ArgumentParser parser = IKArgParser.getArgParser();
        Namespace ns = null;
        try{
           ns = parser.parseArgs(args);
        }
        catch (HelpScreenException e) {
            System.exit(0);
        }

        Integer docId = ns.getInt("docid");
        String query = ns.getString("q");
        String dataDir = ns.getString("datadir");
        Boolean addedToday = ns.getBoolean("addedtoday");
        String docType = ns.getString("doctype");
        String logLevel = ns.getString("loglevel");
        String logFile = ns.getString("logfile");
        String qFile = ns.getString("qfile");

        setUpLogging(logLevel,logFile);

        FileStorage fileStorage = new FileStorage(dataDir);
        IKApi ikapi = new IKApi(ns, fileStorage);


        if ( docId != null && query != null && !query.isEmpty()) {
            ikApiLogger.warning(String.format("Docfragment for %d q: %s", docId, query));
            ikapi.saveDocFragment(docId, query);
        }
        else if (docId!=null) {
            ikapi.downloadDoc(docId, dataDir);
        }
        else if (query != null && !query.isEmpty()) {
            StringBuilder q = new StringBuilder();
            q.append(query);
            if (addedToday)
            {
                q.append(" added:today");
            }
            ikApiLogger.warning(String.format("Search q: %s",q));
            ikapi.saveSearchResults(q.toString());
        }
        else if (docType != null && !docType.isEmpty()) {
            ikapi.downloadDocType(docType);
        }
        else if (qFile != null && !qFile.isEmpty()) {
            List<String> queries = new ArrayList<>();

            Path filePath = Paths.get(qFile);

            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    queries.add(line.trim());
                }
            } catch (IOException e) {
                ikApiLogger.severe(e.getMessage());
            }
            ikapi.executeTasks(queries);
        }
        }
        catch(RuntimeException re)
        {
            ikApiLogger.severe(re.getMessage());
        }

        catch(Exception e)
        {
            ikApiLogger.severe(e.getMessage());
        }

    }

    public static LocalDate getDateObj(String publishDate) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(publishDate);
        int[] parts =new int[3];
        int i=0;
        while (matcher.find() && i<3)
        {
            parts[i++] = Integer.parseInt(matcher.group());
        }
        if (i==3)
        {
            return LocalDate.of(parts[0],parts[1],parts[2]);
        }
        else {
            throw new IllegalArgumentException("Invalid date string: "+ publishDate);
        }
    }
}
