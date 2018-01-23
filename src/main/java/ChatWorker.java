import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class ChatWorker implements Runnable {
    private final Socket socket;
    private final Set<ChatWorker> workers;
    private final Executor pool;
    private final Queue<String> requests;
    private final Queue<String> responses;
    private String name;
    private String whisperer;

    public ChatWorker(final Socket socket, final Set<ChatWorker> workers, final Executor pool) throws IOException {
        this.socket = socket;
        this.workers = workers;
        this.pool = pool;
        this.name = "Chat" + workers.size();

        this.requests = new ConcurrentLinkedQueue<>();
        this.responses = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run() {
        System.out.println(String.format("Client connected: %s", this.socket.getRemoteSocketAddress()));

        try {
            final Runnable input = new InputThread(this.socket, this.requests);
            final Runnable output = new OutputThread(this.socket, this.responses);

            pool.execute(input);
            pool.execute(output);

            boolean running = true;

            while (running) {
                // get request
                String request = null;
                synchronized (requests) {
                    request = requests.poll();
                }
                if (request != null) {
                    System.out.println("Hai");
                    if (request.startsWith("/name")) {
                        String name = request.split(" ").length > 1 ? request.split(" ")[1] : "Chat" + workers.size();
                        this.name = name;
                    } 
                    else if(request.startsWith("/whisper")){
                        String name = request.split(" ")[1];
                        if(name != null){
                            whisper(request.split(" ",3)[2], name);
                        } else {
                            addMessage("/whisper [name] [message]");
                        }
                        
                    }
                    else if(request.startsWith("/r")){
                        if(this.whisperer != null){
                            whisper(request.split(" ")[1], this.whisperer);
                        } else {
                            addMessage("Error: User tidak tersedia");
                        }
                    }
                    else {    
                        switch (request) {
                            case "/stop":
                                addMessage("bye-bye");
                                running = false;
                                break;
                            case "/time":
                                addMessage(new Date().toString());
                                break;
                            case "/clients":
                                addMessage(workers.stream().map(x -> x.name)
                                        .collect(Collectors.joining(", ")));
                                break;
                            case "/memory":
                                addMessage(String.valueOf(Runtime.getRuntime().totalMemory() - Runtime.getRuntime()
                                        .freeMemory()));
                                break;
                            default:
                                if (! request.trim().equals("")) {
                                    System.out.println("wadap");
                                    broadcast(String.format("%s: %s", this.name, request));
                                }
                        }
                    }

                }
                synchronized (requests) {
                    try {
                        requests.wait();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
        }

        try {
            System.out.println(String.format("Client disconnects: %s", this.socket.getRemoteSocketAddress()));
            synchronized (workers) {
                workers.remove(this);
                broadcast(String.format("%s has left", this.name));
            }
            socket.close();
        } catch (IOException e) {
        }
    }

    public void broadcast(final String message) {
        synchronized (workers) {
            workers.stream().filter(x -> x != this).forEach(worker -> {
                worker.addMessage(String.format(message));
            });
        }
    }
    
    public void whisper(String message, String receiver){
        ChatWorker worker = workers.stream().filter(x -> x.name.equals(receiver))
                                            .findAny()
                                            .orElse(null);
        
        if(worker != null){
            System.out.println(worker.name);
            worker.whisperer = this.name;
            worker.addMessage(String.format("From %s: %s", this.name, message));
        }else {
            System.out.println("test");
            this.addMessage("user " + receiver + " is not available");
        }
    }

    public void addMessage(String message) {
        synchronized (responses) {
            responses.offer(message);
            responses.notify();
        }
    }
}
