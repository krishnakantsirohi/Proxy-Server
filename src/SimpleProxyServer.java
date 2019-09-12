/*
 * Name: Krishnakant Sirohi
 * ID: 1001668969
 * This code is referred from the following sources:-
 * https://github.com/stefano-lupo/Java-Proxy-Server
 * https://stackoverflow.com/questions/36301905/how-to-download-export-txt-file-in-java
 * http://hepunx.rl.ac.uk/~adye/javatutorial/networking/sockets/readingWriting.html
 * https://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html
 * https://www.geeksforgeeks.org/multithreading-in-java/
 */

import java.io.*;
import java.net.*;

public class SimpleProxyServer {
    public static void main(String[] args)  {

        try {
            ServerSocket serverSocket = new ServerSocket(5555);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread thread = new Thread(new SimpleProxyServerHandler(clientSocket));
                System.out.print(thread.getState()+" "+thread.getName()+" started on "+ clientSocket.getRemoteSocketAddress()+" for ");
                thread.start();
            }
        }
        catch (Exception e){
            System.out.println(e+"\n");
        }
    }
}
class SimpleProxyServerHandler implements Runnable{

    // Socket for client to ProxyServer connection.
    private Socket clientSocket = null;

    // Writer for reply to client.
    private BufferedWriter clientWriter = null;

    // Reader to read the client requests.
    private BufferedReader clientReader = null;

    public SimpleProxyServerHandler(Socket clientSocket){
        try {
            this.clientSocket = clientSocket;
            this.clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        }
        catch (IOException e)
        {
            System.out.println("Exception "+e+" occured while instantiating the ProxyHandler.");
        }
    }
    /*
     *  Reads request from the client and reply back to the client.
     *  Also checks whether the requested data is present in the cache or not.
     *  If present send the cached data to client.
     */
    @Override
    public void run() {
        String request = "";
        // Get the request from the client.
        try{
            request = clientReader.readLine();
        }
        catch (IOException e)
        {
            System.out.println("\nError "+e+" while reading client request.\n");
        }
        if (request==null)
            return;
        // split the request to extract type of request, url and port no.
        String[] strings = request.split(" ");

        String requestType = strings[0];

        String url;
        // Extract the url frpm request and checks whether it contains http or not. If it does contains http, remove it from the url.
        if (strings[1].contains("http"))
            url = strings[1].split(":")[1].substring(2);
        else
            url = strings[1].split(":")[0];

        if (url.charAt(url.length()-1)=='/')
            url = url.substring(0,url.length()-1);
        // Extract the port no from hte request.
        int port;
        try{
            port = Integer.parseInt(strings[1].substring(strings[1].lastIndexOf(':')+1));
        }
        catch (NumberFormatException e){
            System.out.println(url+"\n\nIncorrect number format. Could not get port no.\n");
            port = 80;
        }
        System.out.println(url+"\n");

        // Check whether the request is of type CONNECT or not.
        if(requestType.equals("CONNECT")){
            clientServerHandler(url,port);
        }
        else{
            File file = new File("Cached_Files/"+url.substring(url.lastIndexOf("/")+1));
            if (file.exists())
                sendCachedFileToClient(file);
            else if (!file.exists() && requestType.equals("GET") && url.contains(".txt"))
                sendNonCachedToClient(url);
            else
                clientServerHandler(url,port);
        }
    }

    //Start a connection to the requested remote server.
    private void clientServerHandler(String url, int port)
    {
        try{
            // Get the actual IP address of the URL from DNS.
            InetAddress address = InetAddress.getByName(url);

            // Open a socket to the remote server.
            Socket serverSocket = new Socket(address,port);

            // Send connection established to client.
            String reply = "HTTP/1.0 200 Connection established\r\n" +
                    "Proxy-Agent: ProxyServer/1.0\r\n" +
                    "\r\n";

            clientWriter.write(reply);
            clientWriter.flush();

            Thread clientServerThread = new Thread()
            {
                InputStream clientStream = clientSocket.getInputStream();
                OutputStream serverStream = serverSocket.getOutputStream();
                public void run() {
                    try{
                        // Reading the client request from client and writing to server directly.
                        byte[] buffer = new byte[4096];
                        int len;
                        do{
                            len = clientStream.read(buffer);
                            if (len>0){
                                serverStream.write(buffer,0,len);
                                // If there is no data available on the stream then we
                                if (clientStream.available()<1){
                                    serverStream.flush();
                                }
                            }
                        }while(len>=0);

                    }
                    catch (IOException e)
                    {
                        System.out.println(e+""+e.getMessage()+"\n");
                    }

                }

            };
            clientServerThread.start();

            System.out.println("Getting data from "+url+" using "+clientServerThread.getState()+" "+clientServerThread.getName()+"\n");

            // Get from server and reply to client while caching the file simultaneously.
            try{
                byte[] buffer = new byte[4096];
                int len;
                do{
                    len = serverSocket.getInputStream().read(buffer);
                    if (len>0){
                        clientSocket.getOutputStream().write(buffer,0,len);
                        if (serverSocket.getInputStream().available()<1){
                            clientSocket.getOutputStream().flush();
                        }
                    }
                }while (len>=0);
            }
            catch (IOException e)
            {
                System.out.println(e+" in client server handler method.\n");
            }
        }
        catch (IOException e)
        {
            System.out.println(e+" in client server handler method.\n");
        }
    }

    /*
     * Send the requested file from cache to client.
     */
    private void sendCachedFileToClient(File file)
    {

        try {
            // Send the HTTP OK to client.
            String response = "HTTP/1.0 200 OK\n" +
                    "Proxy-agent: ProxyServer/1.0\n" +
                    "\r\n";
            clientWriter.write(response);
            clientWriter.flush();

            // Open the file and start reading from file and writing to client.
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            System.out.println("Reading "+file.getName()+" from Cache\n");
            while ((response=bufferedReader.readLine())!=null)
            {
                clientWriter.write(response+"\n");
                clientWriter.flush();
            }
            clientWriter.close();
        }
        catch (IOException e)
        {
            System.out.println(e);
        }
    }
    /*
     * Request file form the server
     */
    private void sendNonCachedToClient(String url)
    {
        try {

            URL url1 = new URL("http://"+url.trim());

            // Open the URL reader.
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(url1.openStream()));
            String response;

            // Create the file.
            File file = new File("Cached_Files/"+url.substring(url.lastIndexOf("/")+1));

            // Open the file to write the data.
            BufferedWriter fileBufferedWriter = new BufferedWriter(new FileWriter(file));

            // Send the HTTP OK to client.
            response = "HTTP/1.0 200 OK\n" +
                    "Proxy-agent: ProxyServer/1.0\n" +
                    "\r\n";;
                    clientWriter.write(response);
                    clientWriter.flush();

            // Reading from the server and writing to the client and caching the file simultaneously..
            while ((response=bufferedReader.readLine())!=null)
            {
                clientWriter.write(response+"\n");
                clientWriter.flush();
                fileBufferedWriter.write(response+"\n");
                fileBufferedWriter.flush();
            }
            clientWriter.close();
            fileBufferedWriter.close();
            bufferedReader.close();
        }
        catch (IOException e){
            System.out.println(e);
        }
        catch (Exception e){
            System.out.println(e);
        }
    }
}
