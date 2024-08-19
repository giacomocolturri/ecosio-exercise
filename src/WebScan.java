import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScan {

    private final Set<String> links = Collections.synchronizedSortedSet(new TreeSet<>());
    private final String domain;

    public WebScan(String startUrl) {
        this.domain = getDomain(startUrl);
        ForkJoinPool pool = new ForkJoinPool();
        System.out.println("Start scanning...\n");
        pool.invoke(new ScanTask(startUrl));
    }

    private class ScanTask extends RecursiveTask<Void> {
        private final String url;

        public ScanTask(String url) {
            this.url = url;
        }

        @Override
        protected Void compute() {
            if (links.contains(url)) return null; // avoid duplicates

            links.add(url);
            Set<ScanTask> subTasks = new HashSet<>();

            try {
                System.out.printf("Thread #%s is doing task for url %s\n\n", Thread.currentThread().getName(), url);
                String html = fetchHtml(url);
                Set<String> foundLinks = extractLinks(html);
                for (String link : foundLinks) {
                    ScanTask task = new ScanTask(link);
                    task.fork();
                    subTasks.add(task);
                }
            } catch (Exception e) {
                System.err.println("Failed to process URL: " + url + "\n");
            }

            for (ScanTask task : subTasks) {
                task.join();
            }

            return null;
        }
    }

    private String fetchHtml(String urlStr) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        in.close();
        return content.toString();
    }

    private Set<String> extractLinks(String content) {
        Set<String> foundLinks = new ConcurrentSkipListSet<>();
        Pattern pattern = Pattern.compile("<a href=\"(http[s]?://[^\"]+)\"");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.contains(domain)) {
                foundLinks.add(url);
            }
        }
        return foundLinks;
    }

    private String getDomain(String url) {
        try {
            return new URI(url).toURL().getHost();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Failed to get domain for URL: " + url, e);        }
    }

    public void printResults() {
        System.out.println("Results size : " + links.size() + "\n");
        for (String link : links) {
            System.out.println(link);
        }
    }

}
