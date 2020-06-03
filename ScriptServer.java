package com.intgroup.htmlcheck;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.text.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Executors;

public class ScriptServer {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Syntax: java ScriptServer port token bashScript");
            return;
        }

        //Parse params
        int port = Integer.parseInt(args[0]);
        String token = args[1];
        String bashScript = args[2];

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);

        server.createContext("/deploy", (httpExchange) -> {
            String method = httpExchange.getRequestMethod().toUpperCase();
            if (method.equals("GET")) {

                //Read request
                String request = httpExchange.
                        getRequestURI()
                        .toString();

                //Parse params
                Map<String, String> params = parseParams(request);

                StringJoiner response = new StringJoiner("\n");

                if (params.getOrDefault("token", "").equals(token)) {
                    try {
                        executeShellScript(bashScript);

                        response.add("{success: true, message: \"Script executed\"}");
                    } catch (Exception ex) {
                        ex.printStackTrace();

                        response.add("{success: false, message: \"Can't call script\"}");
                    }
                } else {
                    response.add("{success: false, message: \"Invalid token\"}");
                }

                //Write response
                OutputStream outputStream = httpExchange.getResponseBody();

                // this line is a must
                httpExchange.sendResponseHeaders(200, response.toString().length());

                outputStream.write(response.toString().getBytes());
                outputStream.flush();
                outputStream.close();
            }

        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        System.out.println("Server started:\nPORT: " + port + "\nSCRIPT: " + bashScript + "\nTOKEN: " + token);
    }

    private static Map<String, String> parseParams(String requestURI) {
        Map<String, String> result = new HashMap<>();

        String[] params = requestURI.split("\\?")[1].split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            result.put(keyValue[0], keyValue[1]);
        }

        return result;
    }

    private static void executeShellScript(String pathToScript) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        // Windows
        processBuilder.command(pathToScript);

        Process process = processBuilder.start();

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();
        System.out.println("\nExited with error code : " + exitCode);
    }
}
