import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.io.*;
import java.util.HashMap;

class HTTPRequest {
    private String file;
    private String method;
    private String version;
    private HashMap<String, String> headers;

    public String getFilePath() {
        return file;
    }
    
    public String getMethod() {
        return method;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getHeaderValue(String headerName) {
        return headers.get(headerName);
    }
    
    public HTTPRequest() {
        file = null;
        method = null;
        //default value in case no HTTP version provided
        version = "1.1";
        headers = null;
    }
    
    /**
     * Parses a request and set all the private variables
     * @param  request a string that was read from the socket and contains the request
     * @return returns true if parsing was successful
     */
    public boolean parseRequest(String request) {
        String[] lines = request.split("\n");
        //parse request line
        String[] requests = lines[0].split(" ");
        boolean isValidRequest = true;
        //only support GET request
        if(!requests[0].equals("GET")) {
            isValidRequest = false;
        } else {
            method = "GET";
        }
        //get HTTP version
        String lastWord = requests[requests.length - 1].substring(0, 5);
        if(lastWord == null || !lastWord.equals("HTTP/")) {
            isValidRequest = false;
        } else {
           String ver = requests[requests.length - 1].substring(5, 8);
           if(ver.equals("1.0")) {
               version = "1.0";
           } else if(ver.equals("1.1")) {
               version = "1.1";
           } else {
               isValidRequest = false;
           }
        }
        if(requests.length != 3) {
          isValidRequest = false;
        } else {
          file = requests[1].substring(1);
        }

        //parse headers
        headers = new HashMap<String, String>();
        for(int i = 1; i < lines.length; i++) {
            String[] keyValue = lines[i].split(":");
            headers.put(keyValue[0].trim().toLowerCase(), keyValue[1].trim().toLowerCase());
        }

        if(!headers.containsKey("host") && version == "1.1") {
          isValidRequest = false;
        }
        
        return isValidRequest;
    }
    
    
    /**
     * Reads a single request from the socket
     * @param  client Socket that handles the client connection
     * @return new HTTPRequest of the parsed request
     * @throws IOException 
     */
    public boolean readRequest(Socket client) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
        StringBuilder request = new StringBuilder();
        String l;
        while ((l = br.readLine()) != null && !(l.equals(""))) {
            request.append(l);
            request.append("\n");
        }
        //get data
        boolean isRequestParsed = parseRequest(request.toString());
        if(isRequestParsed) {
            return true;
        } else {
            return false;
        }

    }
}



public class WebServer {
    public static void main(String[] args) throws IOException {
      int port = 80;
        try {
          port = Integer.parseInt(args[0]);
        } catch (Exception e) {
          System.out.println("Usage: java webserver <port>");
          System.exit(1);
        }

        WebServer serverInstance = new WebServer();
        serverInstance.start(port);
    }

    private void start(int port) throws IOException {
      System.out.println("Starting server on port " + port);
      ServerSocket serverSocket = new ServerSocket(port);

      // set up the sockets and then call handleRequests.
      while(true) {
          try (
              Socket clientSocket = serverSocket.accept();
          ) {
              handleRequests(clientSocket);
          }
      }
    }

    /**
     * Handles requests sent by a client
     * @param  client Socket that handles the client connection
     * @throws IOException 
     */
    private void handleRequests(Socket client) throws IOException {
        HTTPRequest request = new HTTPRequest();
        boolean isValidRequest = request.readRequest(client);
        byte[] response;
        if(isValidRequest) {
            response = getResponse(request);
        } else {
            response = get400Response(request);
        }
        sendResponse(client, response);
        boolean isPersistent = request.getVersion().equals("1.1");
        if(isPersistent) {              
          try {
              client.setSoTimeout(2000);
              handleRequests(client);
          } catch(Exception e) {
              
          }
        }
        
    }


    /**
     * Sends a response back to the client
     * @param  client Socket that handles the client connection
     * @param  response the response that should be send to the client
     * @throws IOException 
     */
    private void sendResponse(Socket client, byte[] response) throws IOException {
      BufferedOutputStream bo = new BufferedOutputStream(client.getOutputStream());
      bo.write(response);
      bo.flush();
    }

    /**
     * Get a response to an HTTPRequest
     * @param  request the HTTP request
     * @return a byte[] that contains the data that should be send to the client
     */
    private byte[] getResponse(HTTPRequest request) {
      String filePath = request.getFilePath();
      byte[] fileContent;
      try {
        fileContent = getFileContent(filePath);
      } catch (Exception e) {
        return get404Response(request);
        
      }
      StringBuilder sb = new StringBuilder();
      sb.append("HTTP/");
      sb.append(request.getVersion()+" ");
      sb.append("200 OK\r\n");
      sb.append("\r\n");
      return concatenate(sb.toString().getBytes(), fileContent);
    }


    /**
     * Get a 404 response for a HTTPRequest
     * @param  request a HTTP request
     * @return a byte[] that contains the data that should be send to the client
     */
    private byte[] get404Response(HTTPRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/");
        sb.append(request.getVersion()+" ");
        sb.append("404 Not Found\r\n");
        sb.append("\r\n");
        sb.append(get404Content(request.getFilePath()));
        return sb.toString().getBytes();
    }

    /**
     * Get a 400 response for a HTTPRequest
     * @param  request a HTTP request
     * @return a byte[] that contains the data that should be send to the client
     */
    private byte[] get400Response(HTTPRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/");
        sb.append(request.getVersion()+" ");
        sb.append("400 Bad Request\r\n");
        sb.append("\r\n");
        sb.append(get400Content());
        return sb.toString().getBytes();
    }

    /**
     * Get the content of a whole file
     * @param  client Socket that handles the client connection
     * @return new HTTPRequest of the parsed request
     */
    private byte[] getFileContent(String filename) throws Exception {
      // We read the whole file into memory. This is bad practice,
      // but it is good enough for this submission.
      // In upcoming assignments it might be important to do
      // it differently.
      // E.g., it would be better to read a few bytes, write them to
      // the socket, read the next bytes, write them to the socket, ....
        return Files.readAllBytes(Paths.get(filename));
    }

    /**
     * Concatenates 2 byte[] into a single byte[]
     * This is a function provided for your convenience.
     * @param  buffer1 a byte array
     * @param  buffer2 another byte array
     * @return concatenation of the 2 buffers
     */
    private byte[] concatenate(byte[] buffer1, byte[] buffer2) {
        byte[] returnBuffer = new byte[buffer1.length + buffer2.length];
        System.arraycopy(buffer1, 0, returnBuffer, 0, buffer1.length);
        System.arraycopy(buffer2, 0, returnBuffer, buffer1.length, buffer2.length);
        return returnBuffer;
    }

    /**
     * Returns a string that represents a 404 error
     * You should use this string as the return website
     * for 404 errors.
     * @param  filename path of the file that caused the 404
     * @return a String that contains a 404 website
     */
    private String get404Content(String filename) {
      // You should not change this function. Use it as it is.
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<head>");
        sb.append("<title>");
        sb.append("404 Not Found");
        sb.append("</title>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<h1>404 Not Found</h1>");
        sb.append("<p>The requested URL <i>" + filename + "</i> was not found on this server.</p>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }
    
    /**
     * Returns a string that represents a 400 error
     * You should use this string as the return website
     * for 400 errors.
     * @return a String that contains a 400 website
     */
    private String get400Content() {
      // You should not change this function. Use it as it is.
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<head>");
        sb.append("<title>");
        sb.append("400 Error");
        sb.append("</title>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<h1>Here is your 400!</h1>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }
}