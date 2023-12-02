import java.net.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class Addowserver {
    // port 11122 per instructions
    private static final int PORT = 11122;
    private static int ts;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("Server is running...");

            // get the ts timeout period
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Get Web server name from the user
            System.out.println("Enter timeout period (in seconds):");
            ts = Integer.parseInt(reader.readLine()) * 1000;

            // Thread pool for clients
            ExecutorService executorService = Executors.newCachedThreadPool();

            while (true) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                // Start a separate handler (thread) for each client
                DatagramSocket clientSocket = new DatagramSocket();
                executorService.execute(new ClientHandler(clientSocket, receivePacket, ts));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private DatagramSocket socket;
    private DatagramPacket receivePacket;

    public ClientHandler(DatagramSocket socket, DatagramPacket receivePacket, int timeout) throws SocketException {
        this.socket = socket;
        this.receivePacket = receivePacket;
        this.socket.setSoTimeout(timeout);
    }

    @Override
    public void run() {
        System.out.println("New Client Handler Created");
        try {
            // Extract client message
            String clientMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // Split the message into sequence number and payload
            int sequenceNumber = Integer.parseInt(clientMessage.substring(0,1));
            String payload = clientMessage.substring(1);
            System.out.println("Received - Sequence Number: " + sequenceNumber + ", Payload: " + payload);

            // Get the web data
            String webData = webRequest(payload);

            // Send data to the client
            sendData(receivePacket.getAddress(), receivePacket.getPort(), webData, sequenceNumber);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close client socket when done
            socket.close();
        }
    }

    private void sendData(InetAddress clientAddress, int clientPort, String data, int initialsequenceNumber) throws IOException {
        // Continue sending data in 1024 chunks until done
        // - wait for acks
        // - when sending EOT signal, end the loop after that ack is received 
        boolean running = true;     
        byte[] fullData = data.getBytes();
        int sequenceNumber = initialsequenceNumber;

        // preparing byte array tools used to concat sequence number to payload
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        DataOutputStream dos = new DataOutputStream(outputStream);

        while (running) {
            
            int totalBytes = fullData.length;
            int offset = 0;

            while (offset < totalBytes) {
                int remainingBytes = totalBytes - offset;

                // bundle the web reply into 1024 byte packets (except last remaining)
                int currentChunkSize = Math.min(1024, remainingBytes);

                byte[] chunk = new byte[currentChunkSize];
                System.arraycopy(fullData, offset, chunk, 0, currentChunkSize);

                // append the sequence number to the message chunk
                dos.write(Integer.toString(sequenceNumber).getBytes());
                dos.write(chunk);
                byte[] sequencedChunk = outputStream.toByteArray( );

                // Send the chunk to client
                socket.send(new DatagramPacket(sequencedChunk, sequencedChunk.length, clientAddress, clientPort));
                System.out.println("Sent data with offset: " + offset);

                // STOP and WAIT. Only send the next chunk if the ack goes through
                 if (ackWait(sequenceNumber)) {
                    offset += currentChunkSize;
                    sequenceNumber = (sequenceNumber + 1) % 2;
                 }
                 dos.flush();
            }
            // EOT
            byte[] EOT = (Integer.toString(sequenceNumber) + "!EOT!").getBytes();
            System.out.println("Sent EOT");
            socket.send(new DatagramPacket(EOT, EOT.length, clientAddress, clientPort));

            // STOP and WAIT. Only send the next chunk if the ack goes through
            if (ackWait(sequenceNumber)) {
                running = false;
            }
        }
        dos.close();
    }

    // TODO: wait according to ts timeout period
    private boolean ackWait(int sequenceNumber) throws SocketTimeoutException, IOException {
        int ackNumber;
        byte[] ackData = new byte[1024];
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);

        // try to receive packet, with timeout
        try {
            socket.receive(ackPacket);

            // Process the ACK
            ackNumber = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));
            if (ackNumber == sequenceNumber) {
                System.out.println("Received ACK: " + ackNumber);
                return true;
            }
            System.out.println("Received Incorrect ACK: " + ackNumber);
        
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout!");
        }
        return false;
    }

    private String webRequest(String data) throws IOException {
        // Create URL from data and send GET request per instructions
        URL url = new URL("https://" + data);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Prepare response by reading from URL
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }
}