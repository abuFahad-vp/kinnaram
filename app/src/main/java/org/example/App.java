package org.example;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashMap;
import java.util.Map;

public class App {
    public static void main(String[] args) throws IOException {
        int port = 8000;

        BlockingQueue<Message> channels = new LinkedBlockingQueue<>();
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);
        Server server = new Server(channels);
        Thread server_thread = new Thread(server);
        server_thread.start();

        // Register a shutdown hook to stop the server gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            try {
                serverSocket.close();
                channels.put(new Message(MessageType.ShutDown, null, null));
                server_thread.join(); // Wait for the server thread to finish
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Server stopped.");
        }));

        while(true) {
            Socket clientSocket = serverSocket.accept();
            Client client = new Client(clientSocket,channels); Thread thread = new Thread(client);
            thread.start();
        }
    }
}

enum MessageType {
    ClientConnected,
    ClientDisconnected,
    NewMessage,
    ShutDown
}

class Message {

    public MessageType messageType;
    public Socket clientSocket;
    public String text;

    public Message(MessageType messageType, Socket clientSocket, String text) {
        this.messageType = messageType;
        this.clientSocket = clientSocket;
        this.text = text;
    }
}

class Server implements Runnable {
    BlockingQueue<Message> channels;
    Map<String, Socket> clients = new HashMap<>();
    public Server(BlockingQueue<Message> channels) {
        this.channels = channels;
    }

    public void run(){
        try {
            main_loop:
            while(true) {
                Message msg = channels.take();
                Socket client = msg.clientSocket;
                String client_address = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                switch (msg.messageType) {
                    case MessageType.ClientConnected:
                        System.out.println("Client " + client_address + " connected.");
                        String connected_msg = "Welcome to the club, Habibi......\n" +
                        "Enter ':quit' to exit from the chat\n";
                        try {
                            client.getOutputStream().write(connected_msg.getBytes());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        clients.put(client_address, client);
                        break;
                    case MessageType.ClientDisconnected:
                        System.out.println("Client " + client_address + " disconnected.");
                        clients.remove(client_address);
                        break;
                    case MessageType.NewMessage:
                        String new_msg = "client " + client_address + " -- " + msg.text + "\n";
                        System.out.print(new_msg);
                        for(Map.Entry<String,Socket> entry: clients.entrySet()) {
                            if(!entry.getKey().equals(client_address)) {
                                try {
                                    OutputStream outputStream = entry.getValue().getOutputStream();
                                    outputStream.write(new_msg.getBytes());
                                }catch(Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        break;
                    case MessageType.ShutDown:
                        for(Map.Entry<String,Socket> entry: clients.entrySet()) {
                            try {
                                entry.getValue().close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        break main_loop;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Client implements Runnable {
    Socket clientSocket;
    BlockingQueue<Message> channels;
    public Client(Socket clientSocket, BlockingQueue<Message> channels) {
        this.clientSocket = clientSocket;
        this.channels = channels;
    }

    public void run() {
        try {
            channels.put(new Message(MessageType.ClientConnected, clientSocket, ""));
            while(true) {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String message = in.readLine();
                if(message == null) {
                    in.close();
                    channels.put(new Message(MessageType.ClientDisconnected, clientSocket, ""));
                    break;
                }
                if(message.equals(":quit")) {
                    in.close();
                    channels.put(new Message(MessageType.ClientDisconnected, clientSocket, ""));
                    break;
                }
                channels.put(new Message(MessageType.NewMessage, clientSocket, message));
            }
        } catch (Exception e1) {
            try {
                channels.put(new Message(MessageType.ClientDisconnected, clientSocket, ""));
            } catch (InterruptedException e2) {
                e2.printStackTrace();
            }
            e1.printStackTrace();
        }
    }
}
