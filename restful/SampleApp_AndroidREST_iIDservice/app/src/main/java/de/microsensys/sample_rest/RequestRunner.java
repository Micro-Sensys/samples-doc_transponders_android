package de.microsensys.sample_rest;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RequestRunner implements Callable<String> {
    public static Future<String> executeRequest(RequestRunner _runner) {
        final ExecutorService service = Executors.newFixedThreadPool(10);
        return service.submit(_runner);
    }

    private final String apiKey;
    private final String method;
    private final String path;

    public RequestRunner(String _apiKey, String _method, String _path) {
        apiKey = _apiKey;
        method = _method;
        path = _path;
    }

    @Override
    public String call() {
        try {
            URL url = new URL(path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("ApiKey", apiKey);

            StringBuilder sb = new StringBuilder();
            Log.d("DEBUG", url.toString());
            Log.d("DEBUG","HTTP response code : " + conn.getResponseCode());
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output).append("\n");
            }
            conn.disconnect();

            if (conn.getResponseCode() != 200)
                throw new Exception("HTTP response code : " + conn.getResponseCode());

            String result = sb.toString();
            while (result.endsWith("\n"))
                result = result.substring(0, result.length()-1);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
