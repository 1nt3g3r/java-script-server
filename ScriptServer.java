import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.io.*;
import java.util.function.Consumer;
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

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/deploy", (httpExchange) -> {
            String method = httpExchange.getRequestMethod().toUpperCase();
            if (method.equals("GET") || method.equals("POST")) {

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
	boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

	ProcessBuilder builder = new ProcessBuilder();
	if (isWindows) {
	    builder.command("cmd.exe", "/c", "dir");
	} else {
	    builder.command("sh", "-c", pathToScript);
	}
	builder.directory(new File(System.getProperty("user.home")));
	Process process = builder.start();
	StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
	Executors.newSingleThreadExecutor().submit(streamGobbler);
	int exitCode = process.waitFor();

        System.out.println("\nExited with error code : " + exitCode);
    }

	private static class StreamGobbler implements Runnable {
	    private InputStream inputStream;
	    private Consumer<String> consumer;
	 
	    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
		this.inputStream = inputStream;
		this.consumer = consumer;
	    }
	 
	    @Override
	    public void run() {
		new BufferedReader(new InputStreamReader(inputStream)).lines()
		  .forEach(consumer);
	    }
}
}
